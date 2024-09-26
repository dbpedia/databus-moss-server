package org.dbpedia.moss;

import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.dbpedia.moss.servlets.MetadataValidateServlet;
import org.dbpedia.moss.servlets.MetadataWriteServlet;
import org.dbpedia.moss.servlets.MossProxyServlet;
import org.dbpedia.moss.servlets.SparqlProxyServlet;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.servlets.MetadataAnnotateServlet;
import org.dbpedia.moss.servlets.MetadataBrowseServlet;
import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.servlets.UserDatabaseServlet;
import org.dbpedia.moss.servlets.LayerServlet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.security.Constraint;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.MultipartConfigElement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.EnumSet;


/**
 * Run to start a jetty server that hosts the moss servlet and makes it
 * accessible
 * via HTTP
 */
public class Main {

    public static String KEY_CONFIG = "config";

    public static Model parseJSONLD(String jsonld, String documentURI) {
        // Convert JSON-LD string to InputStream
        InputStream inputStream = new ByteArrayInputStream(jsonld.getBytes());

        // Create an empty model
        Model model = ModelFactory.createDefaultModel();

        // Parse JSON-LD into the model
        RDFDataMgr.read(model, inputStream, documentURI, Lang.JSONLD);

        return model;
    }

    /**
     * Run to start a jetty server that hosts the moss servlet and makes it
     * accessible
     * via HTTP
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        ARQ.init();

        MossEnvironment environment = MossEnvironment.get();
        System.out.println(environment.toString());

        waitForGstore(environment.getGstoreBaseURL());

        UserDatabaseManager userDatabaseManager = new UserDatabaseManager(environment.getUserDatabasePath());
        IndexerManager indexerManager = new IndexerManager(environment);

        Server server = new Server(8080);


        IdentityService identityService = new DefaultIdentityService();
        server.addBean(identityService);


        Constraint constraint = new Constraint();
        constraint.setName("Lalala");
        constraint.setRoles(new String[] { Constraint.ANY_ROLE });
        constraint.setAuthenticate(true);
        // constraint.setDataConstraint(Constraint.DC_CONFIDENTIAL);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        FilterHolder corsFilterHolder = new FilterHolder(new CorsFilter());

        FilterHolder corsHolder = new FilterHolder(CrossOriginFilter.class);
        corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        corsHolder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        corsHolder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
        corsHolder.setName("cross-origin");

        MultipartConfigElement multipartConfig = new MultipartConfigElement("/tmp");



        // Context handler for the unprotected routes
        ServletContextHandler layerContext = new ServletContextHandler();
        layerContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        layerContext.setContextPath("/layer/*");
        layerContext.addServlet(new ServletHolder(new LayerServlet()), "/*");

        ServletContextHandler sparqlProxyContext = new ServletContextHandler();
        sparqlProxyContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        sparqlProxyContext.setContextPath("/sparql/*");
        sparqlProxyContext.addServlet(new ServletHolder(new SparqlProxyServlet()), "/*");

        // Context handler for the unprotected routes
        ServletContextHandler readContext = new ServletContextHandler();
        readContext.addFilter(corsHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        readContext.setContextPath("/g/*");
        readContext.addServlet(new ServletHolder(new MetadataReadServlet()), "/*");

        // Context handler for the unprotected routes
        ServletContextHandler browseContext = new ServletContextHandler();
        browseContext.addFilter(corsHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        browseContext.setContextPath("/browse/*");
        browseContext.addServlet(new ServletHolder(new MetadataBrowseServlet()), "/*");

        ServletHolder metadataWriteServletHolder = new ServletHolder(new MetadataWriteServlet(indexerManager, userDatabaseManager));
        metadataWriteServletHolder.setInitOrder(0);
        metadataWriteServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        ServletHolder metadataAnnotateServletHolder = new ServletHolder(new MetadataAnnotateServlet(indexerManager));
        metadataAnnotateServletHolder.setInitOrder(0);
        metadataAnnotateServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        ServletHolder proxyServlet = new ServletHolder(new MossProxyServlet(environment.GetLookupBaseURL()));

        FilterHolder authFilterHolder = new FilterHolder(new AuthenticationFilter(new APIKeyValidator(userDatabaseManager)));

        // Context handler for the protected api routes
        ServletContextHandler apiContext = new ServletContextHandler();
        apiContext.setContextPath("/api");
        apiContext.addFilter(corsFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
        apiContext.addServlet(metadataWriteServletHolder, "/save");
        apiContext.addServlet(metadataAnnotateServletHolder, "/annotate");
        apiContext.addServlet(proxyServlet, "/search");
        apiContext.addServlet(new ServletHolder(new MetadataValidateServlet()), "/validate");
        apiContext.addServlet(new ServletHolder(new UserDatabaseServlet(userDatabaseManager)), "/users/*");
        apiContext.addFilter(authFilterHolder, "/save", null);
        apiContext.addFilter(authFilterHolder, "/annotate", null);
        apiContext.addFilter(authFilterHolder, "/users/*", null);

        // Set up handler collection
        HandlerList  handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { readContext, browseContext, layerContext, sparqlProxyContext, apiContext });

        server.setHandler(handlers);

        server.start();
        server.join();
    }

    private static void waitForGstore(String targetUrl) throws URISyntaxException, InterruptedException {
        while (true) {
            try {
                // Create a URL object from the target URL
                URL url = new URI(targetUrl).toURL();

                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set the request method to "GET"
                connection.setRequestMethod("GET");

                // Connect to the URL
                connection.connect();

                // Get the response code
                int responseCode = connection.getResponseCode();

                // Print the response code
                System.out.println("Gstore detected: status code " + responseCode);

                // Disconnect the connection
                connection.disconnect();

                break;

            } catch (IOException e) {
                System.out.println("Error connecting to gstore: " + e.getMessage() + ". Trying again in 1 second.");
            }


            Thread.sleep(1000);
        }
    }

}
