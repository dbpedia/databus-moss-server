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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ModuleHandlerTest {

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
    public void testListModules() throws Exception {
        // Create module first
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "POST",
                "/api/v1/modules", """
                        id: list-test
                        label: List Test Module
                        description: A test module for listing
                        language: text/turtle
                        """);

        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        // List modules
        response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules"
        );

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(response.getContent());

        // Parse as JSON array
        JsonNode list = new ObjectMapper().readTree(response.getContent());
        assertTrue(list.isArray(), "Expected JSON array for listModules");

        // Ensure created module is in the list
        boolean found = false;
        for (JsonNode module : list) {
            if ("list-test".equals(module.get("id").asText())) {
                assertEquals("List Test Module", module.get("label").asText());
                assertEquals("A test module for listing", module.get("description").asText());
                assertEquals("text/turtle", module.get("language").asText());
                found = true;
            }
        }
        assertTrue(found, "Created module should be present in the list");

        // Delete module
        response = TestUtils.sendRequest(tester,
                "DELETE",
                "/api/v1/modules/list-test"
        );
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());

        // List again and ensure it's gone
        response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules"
        );
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        list = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContent());
        for (com.fasterxml.jackson.databind.JsonNode module : list) {
            assertNotEquals("list-test", module.get("id").asText(), "Deleted module should not be present");
        }
    }

    @Test
    public void testGetNonExistentModule() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester, "GET", "/api/v1/modules/missing-id");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testUpdateNonExistentModule() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/missing-id",
                "{\"label\":\"updated\"}"
        );
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testDeleteNonExistentModule() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester, "DELETE", "/api/v1/modules/missing-id");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testCrudLifecycle() throws Exception {
        // Get non-existent
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/lifecycle-id"
        );

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());

        // Create
        response = TestUtils.sendRequest(tester,
                "POST",
                "/api/v1/modules",
                "{\"id\":\"lifecycle-id\",\"label\":\"lifecycle module\"}"
        );

        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        // Get created
        response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/lifecycle-id"
        );

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("lifecycle-id"));

        // Update
        response = TestUtils.sendRequest(tester,
                "PUT",
                "/api/v1/modules/lifecycle-id",
                "{\"label\":\"updated lifecycle\"}"
        );

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        // Get updated
        response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/lifecycle-id"
        );

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("updated"));

        // Delete
        response = TestUtils.sendRequest(tester,
                "DELETE",
                "/api/v1/modules/lifecycle-id"
        );

        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());

        // Get again -> 404
        response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules/lifecycle-id"
        );

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
    }
}
