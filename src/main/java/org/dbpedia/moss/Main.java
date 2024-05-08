package org.dbpedia.moss;

import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.dbpedia.moss.servlets.MetadataWriteServlet;
import org.dbpedia.moss.utils.MossConfiguration;
import org.dbpedia.moss.servlets.MetadataAnnotateServlet;
import org.apache.jena.sparql.function.library.max;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.requests.GstoreConnector;
import org.dbpedia.moss.servlets.LogoutServlet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.openid.OpenIdAuthenticator;
import org.eclipse.jetty.security.openid.OpenIdConfiguration;
import org.eclipse.jetty.security.openid.OpenIdLoginService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;

import jakarta.servlet.MultipartConfigElement;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Run to start a jetty server that hosts the moss servlet and makes it
 * accessible
 * via HTTP
 */
public class Main {

    public static String KEY_CONFIG = "config";
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
        MossConfiguration config = MossConfiguration.Load();

        IndexerManager indexerManager = new IndexerManager(config);
        
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

        /*
        String base = "http://localhost:2000";
        String gstore = "http://localhost:2001";
        String lookup = "http://localhost:2003";
        String context = "https://raw.githubusercontent.com/dbpedia/databus-moss/dev/devenv/context.jsonld";
        */

        MultipartConfigElement multipartConfig = new MultipartConfigElement("/tmp");

        ServletHolder metadataAnnotateServletHolder = new ServletHolder(new MetadataAnnotateServlet());
        metadataAnnotateServletHolder.setInitOrder(0);
        metadataAnnotateServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        // Context handler for the unprotected routes
        ServletContextHandler openContext = new ServletContextHandler();
        openContext.setContextPath("/g");
        openContext.addServlet(new ServletHolder(new MetadataReadServlet()), "/*");

        // Context handler for the protected routes
        ServletContextHandler protectedContext = new ServletContextHandler();
        protectedContext.setContextPath("/*");
        protectedContext.addServlet(new ServletHolder(new LogoutServlet(loginService)), "/auth/logout");

        ServletHolder metadataWriteServletHolder = new ServletHolder(new MetadataWriteServlet(indexerManager));
        metadataWriteServletHolder.setInitOrder(0);
        metadataWriteServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        protectedContext.addServlet(metadataWriteServletHolder, "/api/save");
        protectedContext.addServlet(metadataAnnotateServletHolder, "/api/annotate");


        // ServletHolder servletHolder = protectedContext.addServlet(MetadataAnnotateServlet.class, "/api/annotate");
        // ServletHolder servletHolder = protectedContext.addServlet(MetadataAnnotateServlet.class, "/api/annotate");

        // protectedContext.setSessionHandler(sessionHandler);
        // protectedContext.setSecurityHandler(security);

        // String configPath, String baseURI, String contextURL, String gstoreBaseURL, String lookupBaseURL

        // Set up handler collection
        HandlerList  handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { openContext, protectedContext  });

        server.setHandler(handlers);

        server.start();
        server.join();
    }

}
