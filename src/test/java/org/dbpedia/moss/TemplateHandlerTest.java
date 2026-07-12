package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.modules.ModulesResource;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TemplateHandlerTest {

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
    public void testTemplateCrudLifecycle() {
        var turtleReq = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(turtleReq.getHeader("Accept")).thenReturn(HttpConstants.MediaTypes.TEXT_TURTLE);

        Response created = resource.createModule("id: tpl-module\nlanguage: text/turtle\n");
        assertEquals(Response.Status.CREATED.getStatusCode(), created.getStatus());

        HandlerTestSupport.bindRequest(resource, turtleReq);
        Response response = resource.getModuleTemplate("tpl-module");
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        response = resource.updateModuleTemplate("tpl-module", "not valid ttl");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        String ttlBody = "<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .";
        response = resource.updateModuleTemplate("tpl-module", ttlBody);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        response = resource.getModuleTemplate("tpl-module");
        assertTrue(((String) response.getEntity()).contains("http://ex.org/s"));

        response = resource.deleteModule("tpl-module");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }
}
