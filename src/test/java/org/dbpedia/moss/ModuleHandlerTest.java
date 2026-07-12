package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.modules.ModulesResource;
import org.dbpedia.moss.app.ENV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModuleHandlerTest {

    private ModulesResource resource;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("GSTORE_BASE_URL", "http://localhost:5003");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");
        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));
        resource = new ModulesResource();
    }

    @Test
    public void testListModules() throws Exception {
        Response created = resource.createModule("""
                id: list-test
                label: List Test Module
                description: A test module for listing
                language: text/turtle
                """);
        assertEquals(Response.Status.CREATED.getStatusCode(), created.getStatus());

        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response list = resource.listModules();
        assertEquals(Response.Status.OK.getStatusCode(), list.getStatus());
        String content = HandlerTestSupport.entityAsJson(list);
        assertNotNull(content);

        JsonNode root = new ObjectMapper().readTree(content);
        JsonNode modules = root.path("_embedded").path("modules");
        assertTrue(modules.isArray());

        boolean found = false;
        for (JsonNode module : modules) {
            if ("list-test".equals(module.get("id").asText())) {
                assertEquals("List Test Module", module.get("label").asText());
                found = true;
            }
        }
        assertTrue(found);

        Response deleted = resource.deleteModule("list-test");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), deleted.getStatus());

        list = resource.listModules();
        root = new ObjectMapper().readTree(HandlerTestSupport.entityAsJson(list));
        for (JsonNode module : root.path("_embedded").path("modules")) {
            assertNotEquals("list-test", module.get("id").asText());
        }
    }

    @Test
    public void testGetNonExistentModule() {
        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.getModule("missing-id");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testUpdateNonExistentModule() {
        Response response = resource.updateModule("missing-id", "label: updated");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testDeleteNonExistentModule() {
        Response response = resource.deleteModule("missing-id");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testCrudLifecycle() throws Exception {
        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.getModule("lifecycle-crud-test");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        response = resource.createModule("""
                id: lifecycle-crud-test
                label: lifecycle module
                language: text/turtle
                """);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        response = resource.getModule("lifecycle-crud-test");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(HandlerTestSupport.entityAsJson(response).contains("lifecycle-crud-test"));

        response = resource.updateModule("lifecycle-crud-test", """
                id: lifecycle-crud-test
                label: updated lifecycle
                language: text/turtle
                """);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        response = resource.getModule("lifecycle-crud-test");
        assertTrue(HandlerTestSupport.entityAsJson(response).contains("updated lifecycle"));

        response = resource.deleteModule("lifecycle-crud-test");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        response = resource.getModule("lifecycle-crud-test");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
