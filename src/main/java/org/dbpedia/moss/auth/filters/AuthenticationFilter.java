package org.dbpedia.moss.auth.filters;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dbpedia.moss.users.APIKeyValidator;
import org.dbpedia.moss.users.UserInfo;
import org.dbpedia.moss.auth.OIDCDiscoveryService;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpClientWithProxy;
import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.auth.OIDCDiscoveryDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
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
import jakarta.servlet.http.HttpServletResponse;

public class AuthenticationFilter implements Filter {

    public static final String FILTER_ATTRIBUTE_AUTH_TOKEN = "AUTH_TOKEN";

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final APIKeyValidator apiKeyValidator;

    private OIDCDiscoveryService discoveryService;
    private Cache<String, PublicKey> publicKeyCache;
    private Cache<String, String> tokenCache;
    private Cache<String, String> userDisplayNameCache;
    private Cache<String, String> preferredUsernameCache;

    public AuthenticationFilter(APIKeyValidator apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.discoveryService = OIDCDiscoveryService.getInstance();

        this.publicKeyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();

        this.tokenCache = CacheBuilder.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();

        this.userDisplayNameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();

        this.preferredUsernameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String apiKeyHeader = httpRequest.getHeader("X-API-Key");
        if (apiKeyHeader != null) {
            UserInfo userInfo = apiKeyValidator.getUserInfoForAPIKey(apiKeyHeader);
            if (userInfo != null) {
                request.setAttribute(HttpConstants.OIDC.KEY_SUBJECT, userInfo.getSub());
                chain.doFilter(request, response);
                return;
            }
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        String authHeader = httpRequest.getHeader(HttpConstants.Headers.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String sub = validateToken(token);

            if (sub != null) {
                request.setAttribute(FILTER_ATTRIBUTE_AUTH_TOKEN, token);
                request.setAttribute(HttpConstants.OIDC.KEY_SUBJECT, sub);
                String displayName = userDisplayNameCache.getIfPresent(token);
                if (displayName != null) {
                    request.setAttribute(HttpConstants.OIDC.KEY_NAME, displayName);
                }
                String preferredUsername = preferredUsernameCache.getIfPresent(token);
                if (preferredUsername != null) {
                    request.setAttribute(HttpConstants.OIDC.KEY_PREFERRED_USERNAME, preferredUsername);
                }
            }
        }

        chain.doFilter(request, response);
    }

    private String validateToken(String token) {
        String cachedSub = tokenCache.getIfPresent(token);
        if (cachedSub != null) {
            return cachedSub;
        }

        try {
            DecodedJWT jwt = JWT.decode(token);

            if (!ENV.AUTH_OIDC_ISSUER.equals(jwt.getIssuer())) {
                logger.error("Invalid token issuer: {}", jwt.getIssuer());
                throw new SecurityException("Invalid token issuer");
            }

            if (jwt.getExpiresAtAsInstant().isBefore(Instant.now())) {
                logger.error("Token is expired: {}", jwt.getExpiresAtAsInstant());
                throw new SecurityException("Token is expired");
            }

            PublicKey publicKey = getPublicKey(jwt.getKeyId());
            Algorithm alg = getAlgorithm(jwt.getAlgorithm(), (RSAPublicKey) publicKey);

            JWTVerifier verifier = JWT.require(alg)
                    .withIssuer(ENV.AUTH_OIDC_ISSUER)
                    .acceptLeeway(60)
                    .build();

            verifier.verify(jwt);

            String sub = jwt.getSubject();
            tokenCache.put(token, sub);
            cacheDisplayNameFromJwt(jwt, token);
            cachePreferredUsernameFromJwt(jwt, token);
            return sub;

        } catch (JWTDecodeException e) {
            return validateOpaqueToken(token);
        } catch (Exception e) {
            logger.error("JWT validation failed: {}", e.getMessage());
            return validateOpaqueToken(token);
        }
    }

    private PublicKey getPublicKey(String kid) throws Exception {
        PublicKey cached = publicKeyCache.getIfPresent(kid);
        if (cached != null) {
            return cached;
        }

        PublicKey publicKey = discoveryService.getJwkProvider().get(kid).getPublicKey();
        publicKeyCache.put(kid, publicKey);
        return publicKey;
    }

    private String validateOpaqueToken(String token) {
        try {
            URI issuerUri = new URI(ENV.AUTH_OIDC_ISSUER);
            try (CloseableHttpClient client = HttpClientWithProxy.create(issuerUri.getScheme(), issuerUri.getHost())) {
                String sub = fetchSubFromUserInfo(client, token);
                if (sub != null) {
                    return sub;
                }
                return fetchSubFromIntrospection(client, token);
            }
        } catch (IOException | URISyntaxException e) {
            logger.error("Opaque token validation failed: {}", e.getMessage());
            return null;
        }
    }

    private String fetchSubFromIntrospection(CloseableHttpClient client, String token) throws IOException {
        OIDCDiscoveryDocument discovery = discoveryService.getDiscovery();
        String endpoint = discovery.getIntrospectionEndpoint();

        if (endpoint == null || ENV.AUTH_OIDC_CLIENT_ID == null || ENV.AUTH_OIDC_CLIENT_SECRET == null) {
            return null;
        }

        HttpPost post = new HttpPost(endpoint);
        post.setHeader(HttpConstants.Headers.CONTENT_TYPE, "application/x-www-form-urlencoded");
        String body = "token=" + token
                + "&client_id=" + ENV.AUTH_OIDC_CLIENT_ID
                + "&client_secret=" + ENV.AUTH_OIDC_CLIENT_SECRET;
        post.setEntity(new StringEntity(body));

        try (CloseableHttpResponse response = client.execute(post)) {
            JsonNode json = mapper.readTree(EntityUtils.toString(response.getEntity()));
            if (json.path("active").asBoolean()) {
                String sub = json.path(HttpConstants.OIDC.KEY_SUBJECT).asText();
                tokenCache.put(token, sub);
                String displayName = resolveDisplayName(json);
                if (displayName != null) {
                    userDisplayNameCache.put(token, displayName);
                }
                cachePreferredUsernameFromJson(json, token);
                return sub;
            }
        }
        return null;
    }

    private String fetchSubFromUserInfo(CloseableHttpClient client, String token) throws IOException {
        OIDCDiscoveryDocument discovery = discoveryService.getDiscovery();
        String endpoint = discovery.getUserInfoEndpoint();

        if (endpoint == null) {
            return null;
        }

        HttpGet get = new HttpGet(endpoint + "?access_token=" + token);
        try (CloseableHttpResponse response = client.execute(get)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                JsonNode json = mapper.readTree(EntityUtils.toString(response.getEntity()));
                String sub = json.path(HttpConstants.OIDC.KEY_SUBJECT).asText(null);
                if (sub != null) {
                    tokenCache.put(token, sub);
                    String displayName = resolveDisplayName(json);
                    if (displayName != null) {
                        userDisplayNameCache.put(token, displayName);
                    }
                    cachePreferredUsernameFromJson(json, token);
                    return sub;
                }
            }
        }
        return null;
    }

    private Algorithm getAlgorithm(String algorithm, RSAPublicKey publicKey) {
        return switch (algorithm) {
            case "RS256" -> Algorithm.RSA256(publicKey, null);
            case "RS384" -> Algorithm.RSA384(publicKey, null);
            case "RS512" -> Algorithm.RSA512(publicKey, null);
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }

    private static String resolveDisplayName(DecodedJWT jwt) {
        String name = stringClaim(jwt, HttpConstants.OIDC.KEY_NAME);
        if (name != null) {
            return name;
        }
        String given = stringClaim(jwt, HttpConstants.OIDC.KEY_GIVEN_NAME);
        String family = stringClaim(jwt, HttpConstants.OIDC.KEY_FAMILY_NAME);
        if (given != null || family != null) {
            if (given == null) {
                return family;
            }
            if (family == null) {
                return given;
            }
            return given + " " + family;
        }
        return stringClaim(jwt, HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
    }

    private static String stringClaim(DecodedJWT jwt, String claimName) {
        var claim = jwt.getClaim(claimName);
        if (claim.isNull()) {
            return null;
        }
        String value = claim.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String resolveDisplayName(JsonNode json) {
        String name = jsonText(json, HttpConstants.OIDC.KEY_NAME);
        if (name != null) {
            return name;
        }
        String given = jsonText(json, HttpConstants.OIDC.KEY_GIVEN_NAME);
        String family = jsonText(json, HttpConstants.OIDC.KEY_FAMILY_NAME);
        if (given != null || family != null) {
            if (given == null) {
                return family;
            }
            if (family == null) {
                return given;
            }
            return given + " " + family;
        }
        return jsonText(json, HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
    }

    private static String jsonText(JsonNode json, String field) {
        String t = json.path(field).asText(null);
        return t == null || t.isBlank() ? null : t.trim();
    }

    private void cacheDisplayNameFromJwt(DecodedJWT jwt, String token) {
        String displayName = resolveDisplayName(jwt);
        if (displayName != null) {
            userDisplayNameCache.put(token, displayName);
        }
    }

    private void cachePreferredUsernameFromJwt(DecodedJWT jwt, String token) {
        String username = stringClaim(jwt, HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
        if (username != null) {
            preferredUsernameCache.put(token, username);
        }
    }

    private void cachePreferredUsernameFromJson(JsonNode json, String token) {
        String username = jsonText(json, HttpConstants.OIDC.KEY_PREFERRED_USERNAME);
        if (username != null) {
            preferredUsernameCache.put(token, username);
        }
    }

    @Override
    public void destroy() {
        if (publicKeyCache != null) {
            publicKeyCache.invalidateAll();
        }
        if (tokenCache != null) {
            tokenCache.invalidateAll();
        }
        if (userDisplayNameCache != null) {
            userDisplayNameCache.invalidateAll();
        }
        if (preferredUsernameCache != null) {
            preferredUsernameCache.invalidateAll();
        }
    }
}
