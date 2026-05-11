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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TerminologyHandlerTest {

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
    }

    @Test
    public void testCreateAndGetTerminology() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "POST",
                "/terminologies",
                "{\"id\":\"test-term\",\"label\":\"Test Terminology\",\"language\":\"text/turtle\"}");
        assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());

        response = TestUtils.sendRequest(tester,
                "GET",
                "/terminologies/test-term");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        JsonNode node = new ObjectMapper().readTree(response.getContent());
        assertEquals("test-term", node.get("id").asText());
        assertEquals("Test Terminology", node.get("label").asText());
        assertEquals("text/turtle", node.get("language").asText());

        // Hard-coded deletion
        TestUtils.sendRequest(tester, "DELETE", "/terminologies/test-term");
    }

    @Test
    public void testCreateDuplicateTerminology() throws Exception {
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"dup-term\",\"label\":\"First\",\"language\":\"text/turtle\"}");

        HttpTester.Response response = TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"dup-term\",\"label\":\"Second\",\"language\":\"text/turtle\"}");

        assertEquals(HttpServletResponse.SC_CONFLICT, response.getStatus());

        TestUtils.sendRequest(tester, "DELETE", "/terminologies/dup-term");
    }

    @Test
    public void testListTerminologies() throws Exception {
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"term1\",\"label\":\"Term One\",\"language\":\"text/turtle\"}");
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"term2\",\"label\":\"Term Two\",\"language\":\"application/ld+json\"}");

        HttpTester.Response response = TestUtils.sendRequest(tester,
                "GET",
                "/terminologies");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        JsonNode list = new ObjectMapper().readTree(response.getContent());
        assertTrue(list.isArray());

        boolean found1 = false, found2 = false;
        for (JsonNode t : list) {
            if ("term1".equals(t.get("id").asText())) {
                found1 = true;
            }
            if ("term2".equals(t.get("id").asText())) {
                found2 = true;
            }
        }
        assertTrue(found1 && found2);

        // Hard-coded deletion
        TestUtils.sendRequest(tester, "DELETE", "/terminologies/term1");
        TestUtils.sendRequest(tester, "DELETE", "/terminologies/term2");
    }

    @Test
    public void testUpdateTerminology() throws Exception {
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"up-term\",\"label\":\"Old Label\",\"language\":\"text/turtle\"}");

        HttpTester.Response response = TestUtils.sendRequest(tester,
                "PUT",
                "/terminologies/up-term",
                "{\"label\":\"New Label\"}");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        response = TestUtils.sendRequest(tester, "GET", "/terminologies/up-term");
        JsonNode node = new ObjectMapper().readTree(response.getContent());
        assertEquals("New Label", node.get("label").asText());

        // Hard-coded deletion
        TestUtils.sendRequest(tester, "DELETE", "/terminologies/up-term");
    }

    @Test
    public void testDeleteTerminology() throws Exception {
        TestUtils.sendRequest(tester, "POST", "/terminologies",
                "{\"id\":\"del-term\",\"label\":\"To Delete\",\"language\":\"text/turtle\"}");

        HttpTester.Response response = TestUtils.sendRequest(tester,
                "DELETE",
                "/terminologies/del-term");
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());

        response = TestUtils.sendRequest(tester, "GET", "/terminologies/del-term");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    public void testNonExistentTerminology() throws Exception {
        HttpTester.Response response = TestUtils.sendRequest(tester,
                "GET",
                "/terminologies/missing");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.sendRequest(tester,
                "PUT",
                "/terminologies/missing",
                "{\"label\":\"whatever\"}");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());

        response = TestUtils.sendRequest(tester,
                "DELETE",
                "/terminologies/missing");
        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    @AfterAll
    public static void cleanup() throws Exception {
        if (tester != null) {
            tester.stop();
        }
    }
}
