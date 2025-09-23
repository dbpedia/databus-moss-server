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

public class ModulesServletTest {

    private ServletTester tester;

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

    private HttpTester.Response send(HttpTester.Request request) throws Exception {
        return HttpTester.parseResponse(tester.getResponses(request.generate()));
    }

    @Test
    public void testListModules() throws Exception {
        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");

        HttpTester.Response response = send(request);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("["), "Expected JSON array for listModules");
    }

    @Test
    public void testGetNonExistentModule() throws Exception {
        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules/missing-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");

        HttpTester.Response response = send(request);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testUpdateNonExistentModule() throws Exception {
        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("PUT");
        request.setURI("/api/v1/modules/missing-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        request.setContent("{\"label\":\"updated\"}");

        HttpTester.Response response = send(request);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testDeleteNonExistentModule() throws Exception {
        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("DELETE");
        request.setURI("/api/v1/modules/missing-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");

        HttpTester.Response response = send(request);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testCrudLifecycle() throws Exception {
        // Get non-existent
        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        HttpTester.Response response = send(request);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());

        // Create
        request = HttpTester.newRequest();
        request.setMethod("POST");
        request.setURI("/api/v1/modules");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        request.setContent("{\"id\":\"lifecycle-id\",\"label\":\"lifecycle module\"}");
        response = send(request);
        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        // Get created
        request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        response = send(request);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("lifecycle-id"));

        // Update
        request = HttpTester.newRequest();
        request.setMethod("PUT");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        request.setContent("{\"label\":\"updated lifecycle\"}");
        response = send(request);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        // Get updated
        request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        response = send(request);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("updated"));

        // Delete
        request = HttpTester.newRequest();
        request.setMethod("DELETE");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        response = send(request);
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());

        // Get again -> 404
        request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/api/v1/modules/lifecycle-id");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");
        response = send(request);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
        
    }
}
