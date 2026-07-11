package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.resources.ModulesResource;
import org.dbpedia.moss.utils.ENV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextHandlerTest {

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
    public void testContextCrudLifecycle() {
        Response created = resource.createModule("id: ctx-module\nlanguage: text/turtle\n");
        assertEquals(Response.Status.CREATED.getStatusCode(), created.getStatus());

        HandlerTestSupport.bindRequest(resource, HandlerTestSupport.halRequest());
        Response response = resource.getModuleContext("ctx-module");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        response = resource.updateModuleContext("ctx-module", "{\"@context\":{\"key\":\"value\"}}");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(((String) response.getEntity()).contains("key"));

        response = resource.getModuleContext("ctx-module");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        response = resource.deleteModule("ctx-module");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        response = resource.getModuleContext("ctx-module");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
