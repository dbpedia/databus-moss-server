package org.dbpedia.moss;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.servlets.modules.ModulesServlet;
import org.dbpedia.moss.utils.ENV;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexerHandlerTest {

    private static ServletTester tester;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config/moss-config.yml");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("GSTORE_BASE_URL", "http://localhost:5003");
        ENV.setTestVariable("LOOKUP_BASE_URL", "http://localhost:5002");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");

        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));

        tester = new ServletTester();
        tester.setContextPath("/api/v1");
        tester.addServlet(new ServletHolder(new ModulesServlet()), "/modules/*");
        tester.start();
    }

    @Test
    public void testIndexerCrudLifecycle() throws Exception {

        // create module first
        HttpTester.Response res = TestUtils.sendRequest(tester,
                "POST",
                "/api/v1/modules",
                "{\"id\":\"indexer-module\"}"
        );
        assertEquals(HttpServletResponse.SC_CREATED, res.getStatus());

        // GET non-existent indexer
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/indexer-module/indexer.yml"
        );
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getStatus());

        // PUT indexer
        String yamlContent = "settings:\n  enabled: true";
        res = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/indexer-module/indexer.yml",
                yamlContent
        );
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertTrue(res.getContent().contains("enabled: true"));

        // GET indexer -> found
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/indexer-module/indexer.yml"
        );
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertTrue(res.getContent().contains("enabled: true"));

        // DELETE the module
        res = TestUtils.sendRequest(tester,
                "DELETE",
                "/api/v1/modules/indexer-module"
        );
        assertEquals(HttpServletResponse.SC_NO_CONTENT, res.getStatus());

        // Indexer should be deleted with module
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/indexer-module/indexer.yml"
        );
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
        
    }
}
