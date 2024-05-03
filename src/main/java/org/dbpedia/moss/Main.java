package org.dbpedia.moss;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.authentication.SessionAuthentication;
import org.eclipse.jetty.security.openid.OpenIdAuthenticator;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Collections;

/**
 * Run to start a jetty server that hosts the moss servlet and makes it
 * accessible
 * via HTTP
 */
public class Main {

    /**
     * Run to start a jetty server that hosts the moss servlet and makes it
     * accessible
     * via HTTP
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {


        // Logger log = LoggerFactory.getLogger(Main.class);
        
        Server server = new Server(8080);

        SessionHandler sessionHandler = new SessionHandler();

        String ISSUER = "https://auth.dbpedia.org/realms/dbpedia";
        String CLIENT_ID = "moss-dev";
        String CLIENT_SECRET = "AEvDnimPffljolDr3EQ3AwbjvkO2kwac";
 
        IdentityService identityService = new DefaultIdentityService();
        server.addBean(identityService);
       
        
        Constraint constraint = new Constraint();
        constraint.setName("Lalala");
        constraint.setRoles(new String[] { Constraint.ANY_AUTH });
        constraint.setAuthenticate(true);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        OpenIdConfiguration openIdConfiguration = new OpenIdConfiguration(ISSUER, CLIENT_ID, CLIENT_SECRET);
        openIdConfiguration.addScopes("openid", "email", "profile");
        
        OpenIdAuthenticator openidAuthenticator = new OpenIdAuthenticator(openIdConfiguration, null);

        OpenIdLoginService loginService = new OpenIdLoginService(openIdConfiguration);
        
        ConstraintSecurityHandler security = new ConstraintSecurityHandler();
        security.setConstraintMappings(Collections.singletonList(mapping));
        security.setLoginService(loginService);
        security.setAuthenticator(openidAuthenticator);

        // Context handler for the unprotected route
        
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/*");
        context.addServlet(new ServletHolder(new UnprotectedServlet()), "/unprotected");
        context.addServlet(new ServletHolder(new UnprotectedServlet()), "/unprotected2");

        // Context handler for the protected route
        //logoutContext.setSessionHandler(sessionHandler);
        //logoutContext.setSecurityHandler(security);
         
        // Context handler for the protected route
        ServletContextHandler protectedContext = new ServletContextHandler();
        protectedContext.setContextPath("/protected");
        protectedContext.addServlet(new ServletHolder(new LogoutServlet(loginService)), "/logout");
        protectedContext.addServlet(new ServletHolder(new ProtectedServlet()), "/*");
        protectedContext.setSessionHandler(sessionHandler);
        protectedContext.setSecurityHandler(security);

        // Set up handler collection
        HandlerList  handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { protectedContext, context });
        // handlers.addHandler(protectedContext);

        server.setHandler(handlers);

        server.start();
        server.join();
    }

    // Unprotected Servlet
    private static class UnprotectedServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.getWriter().println("<h1>This is an unprotected route</h1>");
        }
    }

    // Protected Servlet
    private static class ProtectedServlet extends HttpServlet {
      

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.getWriter().println("<h1>This is a protected route</h1>");
    
            // Add logout button
            resp.getWriter().println("<form action='/protected/logout' method='post'>");
            resp.getWriter().println("<input type='submit' value='Logout'>");
            resp.getWriter().println("</form>");
        }

       
    }

    
    // Protected Servlet
    private static class LogoutServlet extends HttpServlet {
      
        private final OpenIdLoginService loginService;
        
        public LogoutServlet(OpenIdLoginService loginService) {
            this.loginService = loginService;
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
            resp.sendRedirect("/unprotected");
        }
    }
}
