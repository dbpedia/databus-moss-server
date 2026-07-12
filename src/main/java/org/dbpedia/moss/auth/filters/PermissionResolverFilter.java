package org.dbpedia.moss.auth.filters;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dbpedia.moss.users.UserDatabaseManager;
import org.dbpedia.moss.auth.OIDCDiscoveryService;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpClientWithProxy;
import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.auth.OIDCDiscoveryDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

public class PermissionResolverFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(PermissionResolverFilter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final UserDatabaseManager userDatabase;

    private OIDCDiscoveryService discoveryService;
    private Cache<String, List<String>> tokenRolesCache;

    public PermissionResolverFilter(UserDatabaseManager userDatabase) {
        this.userDatabase = userDatabase;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.discoveryService = OIDCDiscoveryService.getInstance();
        this.tokenRolesCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String sub = (String) httpRequest.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);

        if (sub == null) {
            try {
                Set<String> permissions = userDatabase.resolveAnonymousPermissions();
                List<String> roles = userDatabase.resolveAnonymousRoles();
                request.setAttribute(HttpConstants.OIDC.KEY_PERMISSIONS, permissions);
                request.setAttribute(HttpConstants.OIDC.KEY_ROLES, roles);
            } catch (Exception e) {
                logger.error("Error resolving anonymous permissions for {}", httpRequest.getRequestURI(), e);
            }
            chain.doFilter(request, response);
            return;
        }

        try {
            ensureUsernameFromPreferred(sub, httpRequest);
            List<String> tokenRoles = extractTokenRoles(httpRequest);
            applyAdminUserBootstrap(sub, httpRequest);

            Set<String> permissions = userDatabase.resolvePermissions(sub, tokenRoles);
            List<String> roles = userDatabase.resolveInternalRoles(sub, tokenRoles);

            request.setAttribute(HttpConstants.OIDC.KEY_PERMISSIONS, permissions);
            request.setAttribute(HttpConstants.OIDC.KEY_ROLES, roles);
        } catch (Exception e) {
            logger.error("Error resolving permissions for {}", httpRequest.getRequestURI(), e);
        }

        chain.doFilter(request, response);
    }

    private void ensureUsernameFromPreferred(String sub, HttpServletRequest request) {
        String preferredUsername = (String) request.getAttribute(HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
        if (preferredUsername == null || preferredUsername.isBlank()) {
            return;
        }
        try {
            userDatabase.ensureUsernameIfUnset(sub, preferredUsername);
        } catch (Exception e) {
            logger.error("Failed to set username from preferred_username for {}", sub, e);
        }
    }

    private void applyAdminUserBootstrap(String sub, HttpServletRequest request) {
        String adminUsers = ENV.AUTH_ADMIN_USERS;
        if (adminUsers == null || adminUsers.isBlank()) {
            return;
        }

        String preferredUsername = (String) request.getAttribute(HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
        boolean whitelisted = Arrays.stream(adminUsers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(username -> username.equals(sub)
                        || (preferredUsername != null && username.equals(preferredUsername)));

        if (whitelisted) {
            try {
                userDatabase.ensureAdminUser(sub);
            } catch (Exception e) {
                logger.error("Failed to bootstrap admin role for {}", sub, e);
            }
        }
    }

    private List<String> extractTokenRoles(HttpServletRequest httpRequest) throws Exception {
        String token = (String) httpRequest.getAttribute(AuthenticationFilter.FILTER_ATTRIBUTE_AUTH_TOKEN);
        if (token == null) {
            return List.of();
        }

        List<String> cached = tokenRolesCache.getIfPresent(token);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        List<String> roles = extractRolesFromJwt(token);
        if (roles.isEmpty()) {
            roles = fetchRolesFromUserInfo(token);
        }
        if (!roles.isEmpty()) {
            tokenRolesCache.put(token, new ArrayList<>(roles));
        }
        return roles;
    }

    private List<String> extractRolesFromJwt(String token) {
        List<String> roles = new ArrayList<>();

        try {
            DecodedJWT jwt = JWT.decode(token);

            if (ENV.AUTH_OIDC_ROLE_CLAIM != null && !ENV.AUTH_OIDC_ROLE_CLAIM.isBlank()) {
                roles.addAll(extractRolesFromJwtPayload(jwt, ENV.AUTH_OIDC_ROLE_CLAIM));
                if (!roles.isEmpty()) {
                    return roles;
                }
            }

            Claim realmAccessClaim = jwt.getClaim(HttpConstants.OIDC.KEY_REALM_ACCESS);
            if (!realmAccessClaim.isMissing()) {
                Map<String, Object> realmAccess = realmAccessClaim.asMap();
                if (realmAccess.containsKey(HttpConstants.OIDC.KEY_ROLES)
                        && realmAccess.get(HttpConstants.OIDC.KEY_ROLES) instanceof List<?> realmRoles) {
                    for (Object role : realmRoles) {
                        roles.add(String.valueOf(role));
                    }
                }
            }

            Claim resourceAccessClaim = jwt.getClaim(HttpConstants.OIDC.KEY_RESOURCE_ACCESS);
            if (!resourceAccessClaim.isMissing() && ENV.AUTH_OIDC_CLIENT_ID != null) {
                Map<String, Object> resourceAccess = resourceAccessClaim.asMap();
                Object clientData = resourceAccess.get(ENV.AUTH_OIDC_CLIENT_ID);
                if (clientData instanceof Map<?, ?> clientMap) {
                    Object clientRoles = clientMap.get(HttpConstants.OIDC.KEY_ROLES);
                    if (clientRoles instanceof List<?> roleList) {
                        for (Object role : roleList) {
                            roles.add(ENV.AUTH_OIDC_CLIENT_ID + "/" + role);
                        }
                    }
                }
            }

            Claim rootRoles = jwt.getClaim(HttpConstants.OIDC.KEY_ROLES);
            if (!rootRoles.isMissing() && rootRoles.asList(String.class) != null) {
                roles.addAll(rootRoles.asList(String.class));
            }

        } catch (JWTDecodeException e) {
            logger.debug("Token is not a JWT, skipping local claim extraction");
        }

        return roles;
    }

    private List<String> extractRolesFromJwtPayload(DecodedJWT jwt, String path) {
        try {
            JsonNode node = mapper.readTree(new String(Base64.getUrlDecoder().decode(jwt.getPayload())));
            for (String part : path.split("\\.")) {
                node = node.path(part);
            }
            List<String> roles = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(n -> roles.add(n.asText()));
            } else if (node.isTextual()) {
                roles.add(node.asText());
            }
            return roles;
        } catch (Exception e) {
            logger.debug("Failed to extract roles from JWT claim path {}", path);
            return List.of();
        }
    }

    private List<String> fetchRolesFromUserInfo(String token) throws Exception {
        OIDCDiscoveryDocument discovery = discoveryService.getDiscovery();
        String endpoint = discovery.getUserInfoEndpoint();
        if (endpoint == null) {
            return new ArrayList<>();
        }

        JsonNode userInfo = fetchUserInfoFromEndpoint(token, endpoint);
        if (userInfo == null) {
            return new ArrayList<>();
        }

        return parseRolesFromJson(userInfo);
    }

    private JsonNode fetchUserInfoFromEndpoint(String token, String endpoint) throws Exception {
        URI uri = new URI(endpoint);
        try (CloseableHttpClient client = HttpClientWithProxy.create(uri.getScheme(), uri.getHost())) {
            HttpGet get = new HttpGet(endpoint);
            get.setHeader(HttpConstants.Headers.AUTHORIZATION, "Bearer " + token);

            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    return mapper.readTree(EntityUtils.toString(response.getEntity()));
                }
                return null;
            }
        }
    }

    private List<String> parseRolesFromJson(JsonNode node) {
        List<String> roles = new ArrayList<>();

        if (ENV.AUTH_OIDC_ROLE_CLAIM != null && !ENV.AUTH_OIDC_ROLE_CLAIM.isBlank()) {
            JsonNode claimNode = node;
            for (String part : ENV.AUTH_OIDC_ROLE_CLAIM.split("\\.")) {
                claimNode = claimNode.path(part);
            }
            if (claimNode.isArray()) {
                claimNode.forEach(n -> roles.add(n.asText()));
                return roles;
            }
            if (claimNode.isTextual()) {
                roles.add(claimNode.asText());
                return roles;
            }
        }

        JsonNode rootRoles = node.get(HttpConstants.OIDC.KEY_ROLES);
        if (rootRoles != null && rootRoles.isArray()) {
            rootRoles.forEach(n -> roles.add(n.asText()));
        }

        JsonNode realmRoles = node.path(HttpConstants.OIDC.KEY_REALM_ACCESS).path(HttpConstants.OIDC.KEY_ROLES);
        if (realmRoles.isArray()) {
            realmRoles.forEach(n -> roles.add(n.asText()));
        }

        if (ENV.AUTH_OIDC_CLIENT_ID != null) {
            JsonNode clientRoles = node.path(HttpConstants.OIDC.KEY_RESOURCE_ACCESS)
                    .path(ENV.AUTH_OIDC_CLIENT_ID)
                    .path(HttpConstants.OIDC.KEY_ROLES);
            if (clientRoles.isArray()) {
                clientRoles.forEach(n -> roles.add(ENV.AUTH_OIDC_CLIENT_ID + "/" + n.asText()));
            }
        }

        return roles;
    }

    @Override
    public void destroy() {
        if (tokenRolesCache != null) {
            tokenRolesCache.invalidateAll();
        }
    }
}
