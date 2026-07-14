package org.dbpedia.moss;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import org.apache.jena.query.ARQ;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.sys.JenaSystem;
import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.terminologies.MossTerminology;
import org.dbpedia.moss.users.APIKeyValidator;
import org.dbpedia.moss.users.UserDatabaseManager;
import org.dbpedia.moss.auth.filters.AuthenticationContainerFilter;
import org.dbpedia.moss.auth.filters.AuthenticationFilter;
import org.dbpedia.moss.auth.filters.CorsFilter;
import org.dbpedia.moss.auth.filters.LoggingExceptionMapper;
import org.dbpedia.moss.auth.filters.PermissionContainerFilter;
import org.dbpedia.moss.auth.filters.PermissionResolverFilter;
import org.dbpedia.moss.auth.filters.RequestLoggingFilter;
import org.dbpedia.moss.entries.EntriesResource;
import org.dbpedia.moss.entries.SaveEntryResource;
import org.dbpedia.moss.facets.FacetsResource;
import org.dbpedia.moss.api.MetadataResource;
import org.dbpedia.moss.modules.ModulesResource;
import org.dbpedia.moss.users.PermissionsResource;
import org.dbpedia.moss.users.RolesResource;
import org.dbpedia.moss.api.SparqlResource;
import org.dbpedia.moss.terminologies.TerminologiesResource;
import org.dbpedia.moss.users.UsersResource;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.storage.GstoreResource;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import jakarta.servlet.DispatcherType;

public class Main {

    private static final String BUILD_NUM = "0.2.0";
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        logger.info("BUILD_NUM: {} ", BUILD_NUM);

        JenaSystem.init();
        ARQ.init();

        logger.info("ENV:\n{} ", ENV.printAll());
        configureLogLevel();

        File configRoot = new File(ENV.CONFIG_PATH);
        MossConfiguration.initialize(configRoot);
        MossConfiguration config = MossConfiguration.get();

        for (MossTerminology terminology : config.getTerminologies()) {
            logger.info("Saving Terminology to Gstore: {} ", terminology.getURI());
            try {
                Lang terminologyLanguage = RDFLanguages.contentTypeToLang(terminology.getLanguage());
                if (terminologyLanguage == null) {
                    logger.error("Unknown or missing language for terminology: {}", terminology.getId());
                    continue;
                }
                String gstoreUri = terminology.getURI() + "." + terminologyLanguage.getFileExtensions().getFirst();
                GstoreResource gstoreTerminologyResource = new GstoreResource(gstoreUri);
                gstoreTerminologyResource.writeModel(
                        terminology.getDataModel(),
                        RDFLanguages.contentTypeToLang(terminology.getLanguage()));
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }

        UserDatabaseManager userDatabaseManager = new UserDatabaseManager(ENV.USER_DATABASE_PATH);

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setRequestHeaderSize(32768);

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setPort(8080);
        server.addConnector(connector);

        ServletContextHandler rootContext = new ServletContextHandler();
        rootContext.setContextPath("");

        rootContext.addFilter(new FilterHolder(new RequestLoggingFilter()), "/*",
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
        rootContext.addFilter(new FilterHolder(new CorsFilter()), "/*",
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));
        rootContext.addFilter(
                new FilterHolder(new AuthenticationFilter(new APIKeyValidator(userDatabaseManager))),
                "/*",
                null);
        rootContext.addFilter(
                new FilterHolder(new PermissionResolverFilter(userDatabaseManager)),
                "/*",
                null);

        ResourceConfig jerseyConfig = new ResourceConfig();
        jerseyConfig.property("jersey.config.server.wadl.disableWadl", true);

        jerseyConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(userDatabaseManager).to(UserDatabaseManager.class);
            }
        });

        jerseyConfig.register(MetadataResource.class);
        jerseyConfig.register(SparqlResource.class);
        jerseyConfig.register(EntriesResource.class);
        jerseyConfig.register(SaveEntryResource.class);
        jerseyConfig.register(UsersResource.class);
        jerseyConfig.register(RolesResource.class);
        jerseyConfig.register(PermissionsResource.class);
        jerseyConfig.register(ModulesResource.class);
        jerseyConfig.register(TerminologiesResource.class);
        jerseyConfig.register(FacetsResource.class);

        jerseyConfig.register(PermissionContainerFilter.class);
        jerseyConfig.register(AuthenticationContainerFilter.class);
        jerseyConfig.register(LoggingExceptionMapper.class);
        jerseyConfig.register(JacksonFeature.class);

        ServletHolder jerseyServlet = new ServletHolder(new ServletContainer(jerseyConfig));
        rootContext.addServlet(jerseyServlet, "/*");

        server.setHandler(rootContext);
        server.start();
        server.join();
    }

    private static void configureLogLevel() {
        if (ENV.MOSS_LOG_LEVEL == null || ENV.MOSS_LOG_LEVEL.isBlank()) {
            return;
        }
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel(ENV.MOSS_LOG_LEVEL, Level.INFO));
        logger.info("Log level set to {}", ENV.MOSS_LOG_LEVEL);
    }
}
