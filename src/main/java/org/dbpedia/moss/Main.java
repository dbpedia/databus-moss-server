package org.dbpedia.moss;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

import org.apache.jena.query.ARQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.sys.JenaSystem;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.servlets.DeleteEntryServlet;
import org.dbpedia.moss.servlets.EntriesServlet;
import org.dbpedia.moss.servlets.IndexerPreviewServlet;
import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.dbpedia.moss.servlets.MetadataValidationServlet;
import org.dbpedia.moss.servlets.ProxyServlet;
import org.dbpedia.moss.servlets.SaveEntryServlet;
import org.dbpedia.moss.servlets.SparqlProxyServlet;
import org.dbpedia.moss.servlets.UserDatabaseServlet;
import org.dbpedia.moss.servlets.facets.FacetServlet;
import org.dbpedia.moss.servlets.modules.ModuleApiServlet;
import org.dbpedia.moss.servlets.terminologies.TerminologyServlet;
import org.dbpedia.moss.utils.Constants;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.RequestMethodFilterWrapper;
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

/**
 * Run to start a jetty server that hosts the moss servlet and makes it
 * accessible via HTTP
 */
public class Main {

    private static final String BUILD_NUM = "0.2.0";

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
     * accessible via HTTP
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        logger.info("BUILD_NUM: {} ", BUILD_NUM);

        JenaSystem.init();
        ARQ.init();

        logger.info("ENV:\n{} ", ENV.printAll());

        File configRoot = new File(ENV.CONFIG_PATH);
        MossConfiguration.initialize(configRoot);

        MossConfiguration config = MossConfiguration.get();

        for (MossTerminology terminology : config.getTerminologies()) {
        
            logger.info("Saving Terminology to Gstore: {} ", terminology.getURI());

            try {
                Lang terminologyLanguage = RDFLanguages.contentTypeToLang(terminology.getLanguage());

                String gstoreUri = terminology.getURI() + "." + terminologyLanguage.getFileExtensions().getFirst();
                GstoreResource gstoreTerminologyResource = new GstoreResource(gstoreUri);
                gstoreTerminologyResource.writeModel(terminology.getDataModel(), RDFLanguages.contentTypeToLang(terminology.getLanguage()));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        UserDatabaseManager userDatabaseManager = new UserDatabaseManager(ENV.USER_DATABASE_PATH);

        IndexerManager indexerManager = new IndexerManager();
        indexerManager.start(1);
        Server server = new Server(8080);

        IdentityService identityService = new DefaultIdentityService();
        server.addBean(identityService);

        Constraint constraint = new Constraint();
        constraint.setName("Authenticate");
        constraint.setRoles(new String[]{Constraint.ANY_ROLE});
        constraint.setAuthenticate(true);
        // constraint.setDataConstraint(Constraint.DC_CONFIDENTIAL);

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        FilterHolder corsFilterHolder = new FilterHolder(new CorsFilter());

        MultipartConfigElement multipartConfig = new MultipartConfigElement("/tmp");

        ServletContextHandler rootContext = new ServletContextHandler();
        rootContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
        rootContext.setContextPath("");

        rootContext.addServlet(new ServletHolder(new SparqlProxyServlet()), "/sparql");
        rootContext.addServlet(new ServletHolder(new SparqlProxyServlet()), "/sparql/");

      
        // Context handler for the unprotected routes
        ServletContextHandler readContext = new ServletContextHandler();
        readContext.addFilter(corsFilterHolder, "*", EnumSet.of(DispatcherType.REQUEST));
        readContext.setContextPath("/g/*");
        readContext.addServlet(new ServletHolder(new MetadataReadServlet()), "/*");

        ServletHolder searchProxyServlet = new ServletHolder(new ProxyServlet(ENV.LOOKUP_BASE_URL + "/api"));

        // Context handler for the protected api routes
        ServletContextHandler apiContext = new ServletContextHandler();
        apiContext.setContextPath("/api/v1");
        apiContext.addFilter(corsFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        AdminFilter adminFilter = new AdminFilter();
        AuthenticationFilter authFilter = new AuthenticationFilter(new APIKeyValidator(userDatabaseManager));
        setupReadOnlyAdminServlet(rootContext, new ModuleApiServlet(indexerManager), "/modules/*", authFilter, adminFilter);
        setupReadOnlyAdminServlet(rootContext, new TerminologyServlet(), "/terminologies/*", authFilter, adminFilter);
        setupReadOnlyAdminServlet(rootContext, new FacetServlet(), "/facets/*", authFilter, adminFilter);

        setupReadOnlyAuthServlet(rootContext, new EntriesServlet(indexerManager, userDatabaseManager), "/entries/*", authFilter);

        FilterHolder authFilterHolder = new FilterHolder(new AuthenticationFilter(new APIKeyValidator(userDatabaseManager)));

        ServletHolder saveEntryServletHolder = new ServletHolder(new SaveEntryServlet(indexerManager, userDatabaseManager));
        saveEntryServletHolder.setInitOrder(0);
        saveEntryServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        ServletHolder deleteEntryServletHolder = new ServletHolder(new DeleteEntryServlet(indexerManager, userDatabaseManager));
        deleteEntryServletHolder.setInitOrder(0);
        deleteEntryServletHolder.getRegistration().setMultipartConfig(multipartConfig);

        apiContext.addFilter(authFilterHolder, "/save-entry", null);
        apiContext.addServlet(saveEntryServletHolder, "/save-entry");

        apiContext.addFilter(authFilterHolder, "/delete-entry", null);
        apiContext.addServlet(deleteEntryServletHolder, "/delete-entry");

        ServletHolder metadataValidationServletHolder = new ServletHolder(new MetadataValidationServlet(userDatabaseManager));
        metadataValidationServletHolder.getRegistration().setMultipartConfig(multipartConfig);
        apiContext.addFilter(authFilterHolder, "/validate-entry", null);
        apiContext.addServlet(metadataValidationServletHolder, "/validate-entry");

        ServletHolder indexerPreviewServlet = new ServletHolder(new IndexerPreviewServlet(userDatabaseManager));
        indexerPreviewServlet.getRegistration().setMultipartConfig(multipartConfig);
        apiContext.addFilter(authFilterHolder, "/get-indexer-preview", null);
        apiContext.addServlet(indexerPreviewServlet, "/get-indexer-preview");

        apiContext.addServlet(searchProxyServlet, "/search");
        // apiContext.addServlet(layerTemplateServlet, "/layers/get-template");

        // apiContext.addServlet(layerIndexerConfigurationServlet, "/layers/get-indexers");
        apiContext.addServlet(new ServletHolder(new UserDatabaseServlet(userDatabaseManager)), "/users/*");
        apiContext.addFilter(authFilterHolder, "/users/*", null);

        // apiContext.addFilter(adminFilterHolder, "/save", null);
        // Set up handler collection
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{
            readContext,
            apiContext,
            rootContext,});

        server.setHandler(handlers);

        server.start();
        server.join();
    }

    private static void setupReadOnlyAdminServlet(ServletContextHandler apiContext, HttpServlet servlet, String path,
            Filter authFilter, Filter adminFilter) {

        ServletHolder servletHolder = new ServletHolder(servlet);
        String[] layerListAllowedMethods = new String[]{
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

    private static void setupReadOnlyAuthServlet(ServletContextHandler apiContext, HttpServlet servlet, String path,
            Filter authFilter) {

        ServletHolder servletHolder = new ServletHolder(servlet);
        String[] layerListAllowedMethods = new String[]{
            Constants.REQ_METHOD_HEAD,
            Constants.REQ_METHOD_GET,
            Constants.REQ_METHOD_OPTIONS
        };

        RequestMethodFilterWrapper authFilterWrapper = new RequestMethodFilterWrapper(authFilter, layerListAllowedMethods);

        apiContext.addServlet(servletHolder, path);
        apiContext.addFilter(new FilterHolder(new CorsFilter()), path, EnumSet.of(DispatcherType.REQUEST));
        apiContext.addFilter(new FilterHolder(authFilterWrapper), path, null);
    }
}
