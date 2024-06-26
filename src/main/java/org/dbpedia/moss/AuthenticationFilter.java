package org.dbpedia.moss;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization logic if needed
        // String ISSUER = "https://auth.dbpedia.org/realms/dbpedia";
        
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String authorizationHeader = httpRequest.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            boolean isValid = validateToken(token);

            if (isValid) {
                chain.doFilter(request, response); // Token is valid, proceed to the next filter or servlet
            } else {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Invalid token");
            }
        } else {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("Authorization header is missing or invalid");
        }
    }

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    private boolean validateToken(String token) {
        // Implement your token validation logic here
        System.out.println("HALLOBALLO");
        return true; // Assume all tokens are valid for this example
    }
}
