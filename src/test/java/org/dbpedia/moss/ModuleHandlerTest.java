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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ModuleHandlerTest {

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
    public void testListModules() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "GET",
                "/api/v1/modules"
        );

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("["), "Expected JSON array for listModules");
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
