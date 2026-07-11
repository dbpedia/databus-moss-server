package org.dbpedia.moss.filters;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dbpedia.moss.services.OIDCDiscoveryService;
import org.dbpedia.moss.utils.AdminAccess;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.HttpClientWithProxy;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.OIDCDiscoveryDocument;
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

public class FetchUserRolesFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(FetchUserRolesFilter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private OIDCDiscoveryService discoveryService;
    private Cache<String, List<String>> rolesCache;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.discoveryService = OIDCDiscoveryService.getInstance();
        this.rolesCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String token = (String) httpRequest.getAttribute(AuthenticationFilter.FILTER_ATTRIBUTE_AUTH_TOKEN);
        String sub = (String) httpRequest.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            List<String> roles = rolesCache.getIfPresent(token);
            if (roles == null) {
                roles = extractRolesFromJwt(token);
                if (roles.isEmpty()) {
                    roles = fetchRolesFromUserInfo(token);
                }
                if (!roles.isEmpty()) {
                    rolesCache.put(token, new ArrayList<>(roles));
                }
            } else {
                roles = new ArrayList<>(roles);
            }

            applyAdminUserWhitelist(roles, sub, httpRequest);
            request.setAttribute(HttpConstants.OIDC.KEY_ROLES, roles);
            request.setAttribute(HttpConstants.OIDC.KEY_IS_ADMIN, AdminAccess.hasAdminRole(roles));
        } catch (Exception e) {
            logger.error("Error processing user roles for {}", httpRequest.getRequestURI(), e);
        }

        chain.doFilter(request, response);
    }

    private void applyAdminUserWhitelist(List<String> roles, String sub, HttpServletRequest request) {
        String adminUsers = ENV.AUTH_ADMIN_USERS;
        if (adminUsers == null || adminUsers.isBlank()) {
            return;
        }

        String preferredUsername = (String) request.getAttribute(HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
        String adminRole = AdminAccess.adminRoleKey();

        boolean whitelisted = Arrays.stream(adminUsers.split(","))
                .map(String::trim)
                .anyMatch(username -> username.equals(sub)
                        || (preferredUsername != null && username.equals(preferredUsername)));

        if (whitelisted && roles.stream().noneMatch(role -> role.equalsIgnoreCase(adminRole))) {
            roles.add(adminRole);
            logger.debug("Granted admin role via AUTH_ADMIN_USERS for subject {}", sub);
        }
    }

    private List<String> extractRolesFromJwt(String token) {
        List<String> roles = new ArrayList<>();

        try {
            DecodedJWT jwt = JWT.decode(token);

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
                            roles.add(ENV.AUTH_OIDC_CLIENT_ID + "/" + String.valueOf(role));
                        }
                    }
                }
            }

        } catch (JWTDecodeException e) {
            logger.debug("Token is not a JWT, skipping local claim extraction");
        }

        return roles;
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

        JsonNode isAdminNode = node.get(HttpConstants.OIDC.KEY_IS_ADMIN);
        if (isAdminNode != null && isAdminNode.asBoolean(false)) {
            roles.add(AdminAccess.adminRoleKey());
        }

        return roles;
    }

    @Override
    public void destroy() {
        if (rolesCache != null) {
            rolesCache.invalidateAll();
        }
    }
}
