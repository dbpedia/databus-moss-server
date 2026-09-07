package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.modules.ModulesResource;
import org.dbpedia.moss.app.ENV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dbpedia.moss.http.HttpConstants;

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

        var jsonLdReq = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(jsonLdReq.getHeader(HttpConstants.Headers.ACCEPT)).thenReturn(HttpConstants.MediaTypes.TEXT_HTML);
        HandlerTestSupport.bindRequest(resource, jsonLdReq);
        response = resource.getModuleContextJsonLd("ctx-module");
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(HttpConstants.MediaTypes.APPLICATION_LD_JSON, response.getMediaType().toString());

        response = resource.deleteModule("ctx-module");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        response = resource.getModuleContext("ctx-module");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }
}
