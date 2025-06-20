package org.dbpedia.moss;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.HttpClientWithProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthenticationFilter implements Filter {


	final static Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private APIKeyValidator apiKeyValidator;

    private Cache<String, PublicKey> publicKeyCache;

    private Cache<String, String> tokenCache;

    private JsonNode discoveryDocument;
    
    private String issuer;

    private String clientId;

    private String clientSecret;
    
    private String discoveryUrl;

    private Instant lastDiscoveryFetchTime;
    

    public AuthenticationFilter(APIKeyValidator apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
       
       
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        issuer = ENV.AUTH_OIDC_ISSUER;
        clientId = ENV.AUTH_OIDC_CLIENT_ID;
        clientSecret = ENV.AUTH_OIDC_CLIENT_SECRET;
        discoveryUrl = ENV.AUTH_OIDC_DISCOVERY_URL;

        JsonNode discoveryDocument = getDiscoveryDocument();

        if(discoveryDocument == null) {
            throw new ServletException("Failed to fetch discovery document");
        }

        publicKeyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES) // Cache expires after 5 minutes
            .build();


        tokenCache =  CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS) // Cache expires after 5 minutes
            .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if(httpRequest.getMethod().equals("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        // Check for X-API-Key header first
        String apiKeyHeader = httpRequest.getHeader("X-API-Key");
        
        if (apiKeyHeader != null) {

            UserInfo userInfo = apiKeyValidator.getUserInfoForAPIKey(apiKeyHeader); 

            if (userInfo != null) {
                request.setAttribute(OIDC_KEY_SUBJECT, userInfo.getSub());
                chain.doFilter(request, response); // API key is valid, proceed to the next filter or servlet
                return;
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Invalid API key");
                return;
            }
        }

        // If X-API-Key header is not present, fall back to Bearer token validation
        String authorizationHeader = httpRequest.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {

            String token = authorizationHeader.substring(7);
            String sub = validateToken(token);

            if (sub != null) {
                request.setAttribute(OIDC_KEY_SUBJECT, sub);

                setUserRolesAndAdminFlag(request, token);

                chain.doFilter(request, response); // Token is valid, proceed to the next filter or servlet
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Authorization header is missing or invalid");
            }
        } else {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Authorization header is missing or invalid");
        }
    }

    private void setUserRolesAndAdminFlag(ServletRequest request, String token) {
        boolean isAdmin = false;
        List<String> roles = new ArrayList<>();

        JsonNode userInfo = fetchUserInfoFromEndpoint(token);

        if (userInfo == null) {
            return;
        }

        JsonNode rolesNode = userInfo.get(OIDC_KEY_ROLES);

        if (rolesNode != null && rolesNode.isArray()) {
            for (JsonNode roleNode : rolesNode) {
                String role = roleNode.asText();
                roles.add(role);
                if (role.equals(ENV.AUTH_ADMIN_ROLE)) {
                    isAdmin = true;
                }
            }
        }

        JsonNode adminNode = userInfo.get(OIDC_KEY_IS_ADMIN);
        if (adminNode != null && adminNode.isBoolean()) {
            isAdmin = adminNode.asBoolean(isAdmin);
        }

        String username = null;

        if (userInfo.has(OIDC_KEY_PREFERRED_USERNAME)) {
            username = userInfo.get(OIDC_KEY_PREFERRED_USERNAME).asText(null);
        }

        if (username == null && userInfo.has(OIDC_KEY_SUBJECT)) {
            username = userInfo.get(OIDC_KEY_SUBJECT).asText(null);
        }

        String users = ENV.AUTH_ADMIN_USERS;

       if (users != null && !users.isBlank() && username != null) {
            isAdmin = Arrays.stream(users.split(","))
                .map(String::trim)
                .anyMatch(username::equals);
        }

        request.setAttribute(OIDC_KEY_ROLES, roles);
        request.setAttribute(OIDC_KEY_IS_ADMIN, isAdmin);
    }


    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    private String validateToken(String token) {

        String cachedSub = tokenCache.getIfPresent(token);

        if (cachedSub != null) {

            logger.debug("Retrieved sub from cache: {}", cachedSub);
            return cachedSub;  // Return cached subject
        }

        try {
            
            DecodedJWT jwt;
            
            try {
                // Check if the token is a JWT
                jwt = JWT.decode(token);
            } catch(JWTDecodeException e) {
                return validateOpaqueToken(token);
            }

            // Handle JWT tokens
            String tokenIssuer = jwt.getIssuer();

            if (!issuer.equals(tokenIssuer)) {
                throw new SecurityException("Invalid token issuer");
            }

            Instant expirationTime = jwt.getExpiresAtAsInstant();
            Instant currentTime = Instant.now(Clock.systemUTC());

            if(expirationTime  == null || expirationTime.isBefore(currentTime)) {
                throw new SecurityException("Token is expired");
            }

            String kid = jwt.getKeyId();
            String sub = jwt.getSubject();

            PublicKey publicKey = publicKeyCache.getIfPresent(kid);

            if(publicKey == null) {
               
                String jwksUrl = discoveryDocument.get(DISCOVERY_KEY_JWKS_URI).asText();
                JwkProvider provider = new UrlJwkProvider(new URI(jwksUrl).toURL());
                Jwk jwk = provider.get(kid);

                publicKey = jwk.getPublicKey();
                publicKeyCache.put(kid, publicKey);
            }
            
            Algorithm alg = getAlgorithmFromHeader(jwt.getAlgorithm(), (RSAPublicKey)publicKey);
            JWTVerifier verifier = JWT.require(alg).withIssuer(tokenIssuer).build();
            verifier.verify(token);
            
            tokenCache.put(token, sub);
            return sub; // Token is valid

        } catch (Exception e) {
            // Fallback to introspection for opaque tokens
            e.printStackTrace();
            return null;
        }
    }

    private String validateOpaqueToken(String token) {
      
        if(discoveryUrl == null) {
            discoveryUrl = issuer + DISCOVERY_DOCUMENT_PATH;
        }
        
        logger.info("Fetching discovery at: {}", discoveryUrl); 

        URI discoveryURI;

        try {
            discoveryURI = new URI(discoveryUrl);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

        String scheme = discoveryURI.getScheme();          
        String targetHost = discoveryURI.getHost();

        try (CloseableHttpClient client = HttpClientWithProxy.create(scheme, targetHost)) {

            logger.debug("Checking opaque token");

            // Try to fetch from the user info endpoint first
            String sub = fetchSubFromUserInfo(client, token);

            if(sub != null) {
                return sub;
            }

            // If that didnt work, try the introspection endpoint
            return fetchSubFromIntrospectionEndpoint(client, token);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null; // Token is invalid
    }

    private String fetchSubFromIntrospectionEndpoint(CloseableHttpClient client, String token) throws IOException {
        
        logger.debug("Retrieving introspection endpoint from discovery.");
        JsonNode introspectNode = getDiscoveryDocument().get(DISCOVERY_KEY_INTROSPECTION_ENDPOINT);

        if(introspectNode == null) {
            logger.debug("Introspection endpoint not found.");
            return null;
        }

        if(clientId == null || clientSecret == null) {
            logger.debug("Client id or secret are not specified.");
            return null;
        }

        String introspectionEndpoint = introspectNode.asText();
        logger.debug("Introspection endpoint found:", introspectionEndpoint);

        try {
            HttpPost post = new HttpPost(introspectionEndpoint);
            post.setHeader(HTTP_HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded");
            String body = "token=" + token + "&client_id=" + clientId + "&client_secret=" + clientSecret;
            post.setEntity(new StringEntity(body));
       
            try (CloseableHttpResponse response = client.execute(post)) {
                
                String responseBody = EntityUtils.toString(response.getEntity());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseJson = mapper.readTree(responseBody);

                if (responseJson.get("active").asBoolean()) {
                    String sub = responseJson.get(OIDC_KEY_SUBJECT).asText();
                    tokenCache.put(token, sub);
                    
                    logger.debug("Retrieved sub from introspection endpoint:");
                    return sub;
                }
            } 

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        
        return null;
    }

    private String fetchSubFromUserInfo(CloseableHttpClient client, String token) {
        
        logger.debug("Retrieving user info endpoint from discovery.");
        // If introspection fails or is missing, fallback to user info endpoint
        JsonNode userInfoEndpointNode = getDiscoveryDocument().get(DISCOVERY_KEY_USERINFO_ENDPOINT);

        if(userInfoEndpointNode == null) {
            logger.debug("User info endpoint not found.");
            return null;
        }

        String userInfoEndpoint = userInfoEndpointNode.asText();

        if (userInfoEndpoint == null || userInfoEndpoint.isEmpty()) {
            logger.debug("User info endpoint not specified.");
            return null;
        }

        
        logger.debug("User info endpoint found: {}", userInfoEndpoint);

        HttpGet get = new HttpGet(userInfoEndpoint + "?access_token=" + token);
        try (CloseableHttpResponse response = client.execute(get)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseJson = mapper.readTree(responseBody);

            if (responseJson.has(OIDC_KEY_SUBJECT)) {
                String sub = responseJson.get(OIDC_KEY_SUBJECT).asText();
                
                logger.debug("Retrieved sub from user info endpoint."); 
                tokenCache.put(token, sub);
                return sub;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private JsonNode fetchUserInfoFromEndpoint(String token) {
        String userInfoEndpoint = discoveryDocument.get(DISCOVERY_KEY_USERINFO_ENDPOINT).asText(null);
        if (userInfoEndpoint == null) {
            return null;
        }

        try (CloseableHttpClient client = HttpClientWithProxy.create("https", new URI(userInfoEndpoint).getHost())) {
            HttpGet get = new HttpGet(userInfoEndpoint);
            get.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

            try (CloseableHttpResponse response = client.execute(get)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(responseBody);
                if (json.has(OIDC_KEY_SUBJECT)) {
                    return json;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch user info from endpoint", e);
        }
        return null;
    }
    
    private Algorithm getAlgorithmFromHeader(String algorithm, RSAPublicKey publicKey) {
        switch (algorithm) {
            case "RS256":
                return Algorithm.RSA256(publicKey, null);
            case "RS384":
                return Algorithm.RSA384(publicKey, null);
            case "RS512":
                return Algorithm.RSA512(publicKey, null);
            default:
                throw new JWTVerificationException("Unsupported algorithm: " + algorithm);
        }
    }

    private JsonNode getDiscoveryDocument()  {
    
        boolean hasExpired = lastDiscoveryFetchTime == null || 
            Duration.between(lastDiscoveryFetchTime, Instant.now()).toMillis() > DISCOVERY_REFRESH_INTERVAL;

        if(!hasExpired) {
            return discoveryDocument;
        }

        if(discoveryUrl == null) {
            discoveryUrl = issuer + DISCOVERY_DOCUMENT_PATH;
        }
        
        logger.info("Fetching discovery at: {}", discoveryUrl); 

        URI discoveryURI;

        try {
            discoveryURI = new URI(discoveryUrl);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

        String scheme = discoveryURI.getScheme();           // "https"
        String targetHost = discoveryURI.getHost(); 

        try (CloseableHttpClient client = HttpClientWithProxy.create(scheme, targetHost)) {

            HttpGet httpGet = new HttpGet(discoveryUrl);
            CloseableHttpResponse response = client.execute(httpGet);
            String responseBody = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            discoveryDocument = mapper.readTree(responseBody);
            
            lastDiscoveryFetchTime = Instant.now();
            
            logger.debug("Discovery fetched at: {}", lastDiscoveryFetchTime); 
            response.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return discoveryDocument;
    }

  


    private final static String DISCOVERY_DOCUMENT_PATH = "/.well-known/openid-configuration";
    
    private final static int DISCOVERY_REFRESH_INTERVAL = 15 * 60 * 1000;

    private final static String DISCOVERY_KEY_INTROSPECTION_ENDPOINT = "introspection_endpoint";

    private final static String DISCOVERY_KEY_USERINFO_ENDPOINT = "userinfo_endpoint";
    
    private final static String DISCOVERY_KEY_JWKS_URI = "jwks_uri";
    
    private final static String HTTP_HEADER_CONTENT_TYPE = "Content-Type";

    public final static String OIDC_KEY_SUBJECT = "sub";

    public static final String OIDC_KEY_ROLES = "roles";

    public static final String OIDC_KEY_IS_ADMIN = "isAdmin";

    public final static String OIDC_KEY_PREFERRED_USERNAME = "preferred_username";
}
