package org.dbpedia.moss;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.servlets.modules.ModuleApiServlet;
import org.dbpedia.moss.utils.ENV;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemplateHandlerTest {

    private static ServletTester tester;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("GSTORE_BASE_URL", "http://localhost:5003");
        ENV.setTestVariable("LOOKUP_BASE_URL", "http://localhost:5002");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");

        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));

        IndexerManager indexerManager = new IndexerManager();
        indexerManager.start(1);

        tester = new ServletTester();
        tester.setContextPath("/api/v1");
        tester.addServlet(new ServletHolder(new ModuleApiServlet(indexerManager)), "/modules/*");
        tester.start();
    }

    @Test
    public void testTemplateCrudLifecycle() throws Exception {
        // create a module with Turtle as language
        HttpTester.Response res = TestUtils.sendRequest(tester,
                "POST",
                "/api/v1/modules",
                "{\"id\":\"tpl-module\",\"language\":\"text/turtle\"}"
        );
        assertEquals(HttpServletResponse.SC_CREATED, res.getStatus());

        // GET template -> not found
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/tpl-module/template.ttl"
        );
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getStatus());

        // PUT invalid RDF (should fail parsing)
        res = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/tpl-module/template.ttl",
                "not valid ttl"
        );
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getStatus());

        // PUT with wrong file extension
        res = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/tpl-module/template.jsonld",
                "<s> <p> <o> ."
        );
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, res.getStatus());

        // PUT valid RDF in Turtle
        String ttlBody = "<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .";
        res = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/tpl-module/template.ttl",
                ttlBody
        );
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertTrue(res.getContent().contains("http://ex.org/s"));

        // GET template -> should return same content
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/tpl-module/template.ttl"
        );
        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertTrue(res.getContent().contains("http://ex.org/s"));

        // DELETE the module
        res = TestUtils.sendRequest(tester,
                "DELETE",
                "/api/v1/modules/tpl-module"
        );
        assertEquals(HttpServletResponse.SC_NO_CONTENT, res.getStatus());

        // GET again -> should be gone
        res = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/tpl-module/template.ttl"
        );
        assertEquals(HttpServletResponse.SC_NOT_FOUND, res.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
        // cleanup temp dirs/files if necessary
    }
}
