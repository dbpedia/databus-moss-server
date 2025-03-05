package org.dbpedia.moss;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.dbpedia.moss.utils.ENV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comes after the Auth filter and checks for a user role.
 */
public class RoleFilter implements Filter {

    private static final String HEADER_OPTIONS = "OPTIONS";
    private static final String JWT_CLAIM_RESOURCE_ACCESS = "resource_access";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";
    private static final Logger logger = LoggerFactory.getLogger(RoleFilter.class);

    private final String requiredRole;


    public RoleFilter(String role) {
        this.requiredRole = role;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("RoleFilter initialized with required role: {}", requiredRole);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (httpRequest.getMethod().equals(HEADER_OPTIONS)) {
            chain.doFilter(request, response);
            return;
        }

        // Get Authorization header
        String authorizationHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(AUTHORIZATION_BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header.");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Authorization token missing or invalid.");
            return;
        }

        String token = authorizationHeader.substring(7);

        try {
            DecodedJWT jwt = JWT.decode(token);

            // Parse `resource_access` claim
            Claim resourceAccessClaim = jwt.getClaim(JWT_CLAIM_RESOURCE_ACCESS);
            
            if (resourceAccessClaim == null) {
                logger.warn("Missing resource_access claim.");
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("User does not have the required role.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode resourceAccessNode = mapper.readTree(resourceAccessClaim.toString());

            // Check if required role is present
            if (!userHasRequiredRole(resourceAccessNode, requiredRole)) {
                logger.warn("User does not have the required role: {}", requiredRole);
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.getWriter().write("User does not have the required role.");
                return;
            }

            chain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error parsing token", e);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Invalid token.");
        }
    }

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    private boolean userHasRequiredRole(JsonNode resourceAccessNode, String requiredRole) {
        String clientName = ENV.AUTH_OIDC_CLIENT_ID;
    
        if (resourceAccessNode.has(clientName)) {
            JsonNode clientRolesNode = resourceAccessNode.get(clientName).get("roles");

            if (clientRolesNode != null && clientRolesNode.isArray()) {
                for (JsonNode roleNode : clientRolesNode) {
                    if (roleNode.asText().equals(requiredRole)) {
                        return true; // User has the required role
                    }
                }
            }
        }

        return false; // Role not found
    }
}
