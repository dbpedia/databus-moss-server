package org.dbpedia.moss;

import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.dbpedia.moss.utils.ENV;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.eclipse.jetty.http.HttpTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataReadServletTest {
    
    private ServletTester tester;

    @BeforeEach
    public void setup() throws Exception {

        ENV.setTestVariable("CONFIG_PATH", "./config/moss-config.yml");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("GSTORE_BASE_URL", "http://localhost:5003");
        ENV.setTestVariable("LOOKUP_BASE_URL", "http://localhost:5002");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");


        tester = new ServletTester();
        tester.setContextPath("/g");
        tester.addServlet(new ServletHolder(new MetadataReadServlet()), "/*");
        tester.start();
    }

    @Test
    public void testReadRoot() throws Exception {

        HttpTester.Request request = HttpTester.newRequest();
        request.setMethod("GET");
        request.setURI("/g/");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "tester");

        HttpTester.Response response = HttpTester.parseResponse(tester.getResponses(request.generate()));

        if (response.getStatus() != HttpServletResponse.SC_OK) {
            System.err.println("Test failed with status code: " + response.getStatus());
            System.err.println(response.getContent());
        }

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), "Unexpected response status");
        assertNotNull(response.getContent(), "Response content is null");
    }
}
