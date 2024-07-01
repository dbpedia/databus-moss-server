package org.dbpedia.moss;

import org.dbpedia.moss.servlets.MetadataReadServlet;
import org.dbpedia.moss.utils.MossEnvironment;
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

        MossEnvironment.setTestVariable("CONFIG_PATH", "./config/moss-config.yml");
        MossEnvironment.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        MossEnvironment.setTestVariable("GSTORE_BASE_URL", "http://localhost:5003");
        MossEnvironment.setTestVariable("LOOKUP_BASE_URL", "http://localhost:5002");
        MossEnvironment.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");

        MossEnvironment env = MossEnvironment.get();
        System.out.println(env.toString());

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
