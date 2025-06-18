package org.dbpedia.moss;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that ensures the current user is marked as an admin.
 * 
 * This filter expects the AuthenticationFilter to have already set the
 * isAdmin flag as a request attribute. If the user is not an admin, the
 * request is rejected with HTTP 403.
 */
public class AdminFilter implements Filter {

    private static final String HEADER_OPTIONS = "OPTIONS";
    private static final Logger logger = LoggerFactory.getLogger(AdminFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("AdminFilter initialized");
    }

    /**
     * Checks if the user is an admin. If not, responds with 403 Forbidden.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Allow preflight CORS requests
        if (httpRequest.getMethod().equals(HEADER_OPTIONS)) {
            chain.doFilter(request, response);
            return;
        }

        // Check admin flag set by AuthenticationFilter
        Object isAdminAttr = request.getAttribute(AuthenticationFilter.OIDC_KEY_IS_ADMIN);

        if (!(isAdminAttr instanceof Boolean) || !((Boolean) isAdminAttr)) {
            logger.warn("Access denied: user is not admin");
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("User is not an admin.");
            return;
        }

        // Proceed with the request if admin
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // No cleanup necessary
    }
}
