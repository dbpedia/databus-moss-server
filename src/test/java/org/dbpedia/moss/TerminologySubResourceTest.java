package org.dbpedia.moss;

import java.io.File;

import javax.servlet.http.HttpServletResponse;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.servlets.terminologies.TerminologyServlet;
import org.dbpedia.moss.utils.ENV;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TerminologySubResourceTest {

    private static ServletTester tester;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");

        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));

        tester = new ServletTester();
        tester.setContextPath("");
        tester.addServlet(new ServletHolder(new TerminologyServlet()), "/terminologies/*");
        tester.start();

        // Create a terminology to use in sub-resource tests
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"subres-term\",\"label\":\"SubResource Term\",\"language\":\"text/turtle\"}");
    }

    @Test
    public void testIndexerSubResource() throws Exception {

        String indexerContent = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        // PUT indexer.sparql
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "PUT",
                "/terminologies/subres-term/indexer.sparql",
                indexerContent);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("SELECT"));

        // GET indexer.sparql
        response = TestUtils.sendRequest(tester,
                "GET",
                "/terminologies/subres-term/indexer.sparql");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("SELECT"));

        // DELETE indexer.sparql
        response = TestUtils.sendRequest(tester,
                "DELETE",
                "/terminologies/subres-term/indexer.sparql");
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    }

    @Test
    public void testDataSubResource() throws Exception {
        String rdfData = "<http://example.org/s> <http://example.org/p> <http://example.org/o> .";

        // Determine filename from language
        String dataFilename = "data.ttl";

        // PUT data.ttl
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "PUT",
                "/terminologies/subres-term/" + dataFilename,
                rdfData);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("example.org"));

        // GET data.ttl
        response = TestUtils.sendRequest(tester,
                "GET",
                "/terminologies/subres-term/" + dataFilename);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContent().contains("example.org"));

        // DELETE data.ttl
        response = TestUtils.sendRequest(tester,
                "DELETE",
                "/terminologies/subres-term/" + dataFilename);
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
        if (tester != null) {
            // Delete the terminology
            TestUtils.sendRequest(tester, "DELETE", "/terminologies/subres-term");
            tester.stop();
        }
    }
}
