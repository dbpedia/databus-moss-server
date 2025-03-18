package org.dbpedia.moss;

import org.dbpedia.moss.servlets.MetadataWriteServlet;
import org.dbpedia.moss.servlets.MossProxyServlet;
import org.dbpedia.moss.servlets.ResourceServlet;
import org.dbpedia.moss.servlets.SparqlProxyServlet;
import org.dbpedia.moss.utils.Constants;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.MossContext;
import org.dbpedia.moss.utils.RequestMethodFilterWrapper;
import org.dbpedia.moss.servlets.MetadataBrowseServlet;
import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sys.JenaSystem;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.indexer.OntologyLoader;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.servlets.UserDatabaseServlet;
import org.dbpedia.moss.servlets.indexers.IndexerListServlet;
import org.dbpedia.moss.servlets.indexers.IndexerResourceServlet;
import org.dbpedia.moss.servlets.indexers.IndexerServlet;
import org.dbpedia.moss.servlets.layers.LayerListServlet;
import org.dbpedia.moss.servlets.layers.LayerResourceServlet;
import org.dbpedia.moss.servlets.layers.LayerServlet;
import org.dbpedia.moss.servlets.LayerShaclServlet;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServlet;

import java.io.ByteArrayInputStream;
import java.io.File;
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

    private static final String BUILD_NUM = "0.1.1.1";

    public static String KEY_CONFIG = "config";
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

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

        logger.info("BUILD_NUM: {} ", BUILD_NUM);

        JenaSystem.init();
        ARQ.init();


        logger.info("ENV:\n{} ", ENV.printAll());
        
        File configFile = new File(ENV.CONFIG_PATH);

        MossConfiguration.initialize(configFile);
        MossContext.initialize();

      
        
        waitForGstore(ENV.GSTORE_BASE_URL);

        MossConfiguration config = MossConfiguration.get();
        OntologyLoader.load(config);


        UserDatabaseManager userDatabaseManager = new UserDatabaseManager(ENV.USER_DATABASE_PATH);

        
        IndexerManager indexerManager = new IndexerManager(MossConfiguration.get().getIndexingGroups());
        indexerManager.start(1);
        Server server = new Server(8080);

        IdentityService identityService = new DefaultIdentityService();
        server.addBean(identityService);


        Constraint constraint = new Constraint();
        constraint.setName("Authenticate");
        constraint.setRoles(new String[] { Constraint.ANY_ROLE });
        constraint.setAuthenticate(true);
        // constraint.setDataConstraint(Constraint.DC_CONFIDENTIAL);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        FilterHolder corsFilterHolder = new FilterHolder(new CorsFilter());

        MultipartConfigElement multipartConfig = new MultipartConfigElement("/tmp");

        ServletContextHandler sparqlProxyContext = new ServletContextHandler();
        sparqlProxyContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
        sparqlProxyContext.setContextPath("/sparql");
        sparqlProxyContext.addServlet(new ServletHolder(new SparqlProxyServlet()), "/*");



        // Context handler for the unprotected routes
        ServletContextHandler resourceContext = new ServletContextHandler();
        resourceContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        resourceContext.setContextPath("/res/*");
        resourceContext.addServlet(new ServletHolder(new ResourceServlet()), "/*");

        ServletContextHandler indexerContext = createSimpleContext("/indexer/", new IndexerResourceServlet());
        ServletContextHandler layerContext = createSimpleContext("/layer/", new LayerResourceServlet());
                
        // Context handler for the unprotected routes
        ServletContextHandler readContext = new ServletContextHandler();
        readContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        readContext.setContextPath("/g/*");
        readContext.addServlet(new ServletHolder(new MetadataReadServlet()), "/*");

        // Context handler for the unprotected routes
        ServletContextHandler browseContext = new ServletContextHandler();
        browseContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        browseContext.setContextPath("/file/*");
        browseContext.addServlet(new ServletHolder(new MetadataBrowseServlet()), "/*");

        ServletHolder metadataWriteServletHolder = new ServletHolder(new MetadataWriteServlet(indexerManager, userDatabaseManager));
        metadataWriteServletHolder.setInitOrder(0);
        metadataWriteServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        ServletHolder searchProxyServlet = new ServletHolder(new MossProxyServlet(ENV.LOOKUP_BASE_URL));
        ServletHolder layerShaclServlet = new ServletHolder(new LayerShaclServlet());
        // ServletHolder layerTemplateServlet = new ServletHolder(new LayerTemplateServlet());
        // ServletHolder layerIndexerConfigurationServlet = new ServletHolder(new LayerIndexerConfigurationServlet());

        AuthenticationFilter authFilter = new AuthenticationFilter(new APIKeyValidator(userDatabaseManager));

        FilterHolder authFilterHolder = new FilterHolder(new AuthenticationFilter(new APIKeyValidator(userDatabaseManager)));
        // Context handler for the protected api routes
        ServletContextHandler apiContext = new ServletContextHandler();
        apiContext.setContextPath("/api");
        apiContext.addFilter(corsFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
        apiContext.addServlet(metadataWriteServletHolder, "/save");
        apiContext.addServlet(searchProxyServlet, "/search");


        apiContext.addServlet(layerShaclServlet, "/layers/get-shacl");
        // apiContext.addServlet(layerTemplateServlet, "/layers/get-template");

        
        RoleFilter adminFilter = new RoleFilter(ENV.ADMIN_ROLE);

        setupReadOnlyAdminServlet(apiContext, new IndexerServlet(), "/indexers/*", authFilter, adminFilter);
        setupReadOnlyAdminServlet(apiContext, new LayerServlet(), "/layers/*", authFilter, adminFilter);
        setupReadOnlyAdminServlet(apiContext, new LayerListServlet(), "/layers", authFilter, adminFilter);
        setupReadOnlyAdminServlet(apiContext, new IndexerListServlet(), "/indexers", authFilter, adminFilter);
        

        // apiContext.addServlet(layerIndexerConfigurationServlet, "/layers/get-indexers");
        apiContext.addServlet(new ServletHolder(new UserDatabaseServlet(userDatabaseManager)), "/users/*");
        apiContext.addFilter(authFilterHolder, "/save", null);
        apiContext.addFilter(authFilterHolder, "/users/*", null);

        
        // apiContext.addFilter(adminFilterHolder, "/save", null);

        // Set up handler collection
        HandlerList  handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { 
            readContext, 
            indexerContext,
            layerContext,
            resourceContext, 
            browseContext, 
            apiContext,
            sparqlProxyContext,  
        });

        server.setHandler(handlers);

        server.start();
        server.join();
    }

    private static ServletContextHandler createSimpleContext(String path, HttpServlet servlet) {
        FilterHolder corsFilterHolder = new FilterHolder(new CorsFilter());

        ServletContextHandler context = new ServletContextHandler();
        context.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        context.setContextPath(path);
        context.addServlet(new ServletHolder(servlet), "/*");
        return context;
    }

    private static void setupReadOnlyAdminServlet(ServletContextHandler apiContext, HttpServlet servlet, String path,
        Filter authFilter, Filter adminFilter) {

        ServletHolder servletHolder = new ServletHolder(servlet);
        String[] layerListAllowedMethods = new String[] { 
            Constants.REQ_METHOD_HEAD, 
            Constants.REQ_METHOD_GET, 
            Constants.REQ_METHOD_OPTIONS 
        };

        RequestMethodFilterWrapper authFilterWrapper = new RequestMethodFilterWrapper(authFilter, layerListAllowedMethods);
        RequestMethodFilterWrapper adminFilterWrapper = new RequestMethodFilterWrapper(adminFilter, layerListAllowedMethods);

        apiContext.addServlet(servletHolder, path);
        apiContext.addFilter(new FilterHolder(authFilterWrapper), path, null);
        apiContext.addFilter(new FilterHolder(adminFilterWrapper), path, null);
    }    

    


    private static void waitForGstore(String targetUrl) throws URISyntaxException, InterruptedException, IOException {
        
        int attempts = 0;
        int maxAttempts = 20;

        while (true) {
            try {
                // Create a URL object from the target URL
                URL url = new URI(targetUrl).toURL();

                logger.info("Connecting to gstore at: {} ", targetUrl);

                // Open a connection to the URL
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Set the request method to "GET"
                connection.setRequestMethod("GET");

                // Connect to the URL
                connection.connect();

                // Get the response code
                int responseCode = connection.getResponseCode();

                // Print the response code
                logger.info("Gstore detected: status code {} ", responseCode);

                // Disconnect the connection
                connection.disconnect();

                break;

            } catch (IOException e) {
                
                attempts++;

                if(attempts > maxAttempts) {
                    logger.error("Error connecting to gstore after {} attempts: {}", attempts, e.getMessage());
                    throw new IOException(e);
                }
                else {
                    logger.info("Error connecting to gstore. Trying again in 1 second.");
                }
            }

            Thread.sleep(1000);
        }
    }

}
