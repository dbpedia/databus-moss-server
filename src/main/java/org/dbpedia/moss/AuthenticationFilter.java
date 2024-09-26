package org.dbpedia.moss;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserInfo;

import java.net.URI;
import java.net.URISyntaxException;

public class AuthenticationFilter implements Filter {

    private APIKeyValidator apiKeyValidator;

    private HashMap<String, PublicKey> publicKeyCache;

    public AuthenticationFilter(APIKeyValidator apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
        publicKeyCache = new HashMap<String, PublicKey>();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization logic if needed
        // String ISSUER = "https://auth.dbpedia.org/realms/dbpedia";
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if(httpRequest.getMethod() == "OPTIONS") {
            chain.doFilter(request, response);
            return;
        }


        // Check for X-API-Key header first
        String apiKeyHeader = httpRequest.getHeader("X-API-Key");
        
        if (apiKeyHeader != null) {

            UserInfo userInfo = apiKeyValidator.getUserInfoForAPIKey(apiKeyHeader); 

            if (userInfo != null) {
                request.setAttribute("sub", userInfo.getSub());
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
                request.setAttribute("sub", sub);
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

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    private String validateToken(String token) {
        try {
            // Parse the token
            DecodedJWT jwt = JWT.decode(token);

            // Get the issuer URL
            String issuer = jwt.getIssuer();

            // Get the kid from the header
            String kid = jwt.getKeyId();

            String sub = jwt.getSubject();

            // Create a cache key from issuer and kid (key id)
            String cacheKey = issuer + CACHE_KEY_SEPARATOR + kid;

            PublicKey publicKey;

            if(publicKeyCache.containsKey(cacheKey)) {
                publicKey = publicKeyCache.get(cacheKey);
            } else {
                // Retrieve the well-known document for the JWKS URL
                String wellKnownUrl = issuer + "/.well-known/openid-configuration";
                String jwksUrl = getJwksUrl(wellKnownUrl);

                // Retrieve the JWKS document
                JwkProvider provider = new UrlJwkProvider(new URI(jwksUrl).toURL());
                Jwk jwk = provider.get(kid);

                publicKey = jwk.getPublicKey();
                publicKeyCache.put(cacheKey, publicKey);
            }
           
            // Get the algorithm from the header
            Algorithm alg = getAlgorithmFromHeader(jwt.getAlgorithm(), (RSAPublicKey)publicKey);

            // Validate the token signature
            JWTVerifier verifier = JWT.require(alg).withIssuer(issuer).build();
            verifier.verify(token);

            return sub; // Token is valid

        } catch (JWTVerificationException | JwkException | IOException | URISyntaxException e) {
            e.printStackTrace();
            return null; // Token is invalid
        }
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

    private String getJwksUrl(String wellKnownUrl) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(wellKnownUrl);
        CloseableHttpResponse response = client.execute(httpGet);

        try {
            String responseBody = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(responseBody);
            return jsonNode.get("jwks_uri").asText();
        } finally {
            response.close();
        }
    }

    private final static String CACHE_KEY_SEPARATOR = "_____";
}
