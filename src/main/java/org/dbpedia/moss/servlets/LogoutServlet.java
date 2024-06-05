package org.dbpedia.moss.servlets;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.eclipse.jetty.security.authentication.SessionAuthentication;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.server.UserIdentity;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */

// Protected Servlet
public class LogoutServlet extends HttpServlet {
    
    private final OpenIdLoginService loginService;
    
    public LogoutServlet(OpenIdLoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.getWriter().println("<h1>This is another protected route</h1>");
        
        HttpSession session = req.getSession(false); // false means do not create a new session if not exists

        if (session != null) {
            // Retrieve the SessionAuthentication from the HttpSession
            SessionAuthentication authentication = (SessionAuthentication) session.getAttribute(SessionAuthentication.__J_AUTHENTICATED);
            
            if (authentication != null) {
                // Retrieve the UserIdentity from the SessionAuthentication
                UserIdentity userIdentity = authentication.getUserIdentity();
                
                // Now you have the UserIdentity, you can use it as needed
                loginService.logout(userIdentity);
            }

            session.invalidate();
        }
        

        // Redirect the user to a logout confirmation page or any other appropriate page
        resp.sendRedirect("/g");
    }
}