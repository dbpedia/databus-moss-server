package org.dbpedia.moss;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.dbpedia.moss.http.HttpConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

final class HandlerTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HandlerTestSupport() {}

    static HttpServletRequest halRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(HttpConstants.Headers.ACCEPT))
                .thenReturn(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
        return req;
    }

    static void bindRequest(Object resource, HttpServletRequest req) {
        try {
            var field = resource.getClass().getDeclaredField("request");
            field.setAccessible(true);
            field.set(resource, req);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static String entityAsJson(Response response) throws Exception {
        Object entity = response.getEntity();
        if (entity instanceof String s) {
            return s;
        }
        if (entity instanceof ObjectNode node) {
            return MAPPER.writeValueAsString(node);
        }
        return MAPPER.writeValueAsString(entity);
    }
}
