package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.terminologies.TerminologiesResource;
import org.dbpedia.moss.app.ENV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TerminologyHandlerTest {

    private TerminologiesResource resource;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");
        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));
        resource = new TerminologiesResource();
    }

    @Test
    public void testCreateAndGetTerminology() throws Exception {
        Response created = resource.createTerminology("""
                id: test-term-handler
                label: Test Terminology
                language: text/turtle
                """);
        assertEquals(Response.Status.CREATED.getStatusCode(), created.getStatus());

        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.getTerminology("test-term-handler");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JsonNode node = new ObjectMapper().readTree(HandlerTestSupport.entityAsJson(response));
        assertEquals("test-term-handler", node.get("id").asText());

        resource.deleteTerminology("test-term-handler");
    }

    @Test
    public void testCreateDuplicateTerminology() {
        resource.createTerminology("id: dup-term\nlabel: First\nlanguage: text/turtle\n");
        Response response = resource.createTerminology("id: dup-term\nlabel: Second\nlanguage: text/turtle\n");
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        resource.deleteTerminology("dup-term");
    }

    @Test
    public void testListTerminologies() throws Exception {
        resource.createTerminology("id: term1\nlabel: Term One\nlanguage: text/turtle\n");
        resource.createTerminology("id: term2\nlabel: Term Two\nlanguage: application/ld+json\n");

        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.listTerminologies();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        JsonNode root = new ObjectMapper().readTree(HandlerTestSupport.entityAsJson(response));
        JsonNode list = root.path("_embedded").path("terminologies");
        assertTrue(list.isArray());

        boolean found1 = false;
        boolean found2 = false;
        for (JsonNode t : list) {
            if ("term1".equals(t.get("id").asText())) {
                found1 = true;
            }
            if ("term2".equals(t.get("id").asText())) {
                found2 = true;
            }
        }
        assertTrue(found1 && found2);

        resource.deleteTerminology("term1");
        resource.deleteTerminology("term2");
    }

    @Test
    public void testUpdateTerminology() throws Exception {
        resource.createTerminology("id: up-term\nlabel: Old Label\nlanguage: text/turtle\n");
        Response response = resource.updateTerminology("up-term", """
                id: up-term
                label: New Label
                language: text/turtle
                """);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        response = resource.getTerminology("up-term");
        JsonNode node = new ObjectMapper().readTree(HandlerTestSupport.entityAsJson(response));
        assertEquals("New Label", node.get("label").asText());
        resource.deleteTerminology("up-term");
    }

    @Test
    public void testDeleteTerminology() {
        resource.createTerminology("id: del-term\nlabel: To Delete\nlanguage: text/turtle\n");
        Response response = resource.deleteTerminology("del-term");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        response = resource.getTerminology("del-term");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testNonExistentTerminology() {
        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.getTerminology("missing");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        response = resource.updateTerminology("missing", "label: whatever\nid: missing\n");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        response = resource.deleteTerminology("missing");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
