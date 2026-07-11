package org.dbpedia.moss;

import java.io.File;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.resources.TerminologiesResource;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.HttpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TerminologySubResourceTest {

    private TerminologiesResource resource;

    @BeforeEach
    public void setup() throws Exception {
        ENV.setTestVariable("CONFIG_PATH", "./config");
        ENV.setTestVariable("MOSS_BASE_URL", "http://localhost:8080");
        ENV.setTestVariable("USER_DATABASE_PATH", "./devenv/users.db");
        MossConfiguration.initialize(new File(ENV.CONFIG_PATH));
        resource = new TerminologiesResource();
        resource.createTerminology("""
                id: subres-term
                label: SubResource Term
                language: text/turtle
                """);
    }

    @Test
    public void testDataSubResource() {
        var turtleReq = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(turtleReq.getHeader("Accept")).thenReturn(HttpConstants.MediaTypes.TEXT_TURTLE);

        String rdfData = "<http://example.org/s> <http://example.org/p> <http://example.org/o> .";
        Response response = resource.updateTerminologyData("subres-term", rdfData);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        HandlerTestSupport.bindRequest(resource, turtleReq);
        response = resource.getTerminologyData("subres-term");
        assertTrue(((String) response.getEntity()).contains("example.org"));

        response = resource.deleteTerminologyData("subres-term");
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        resource.deleteTerminology("subres-term");
    }
}
