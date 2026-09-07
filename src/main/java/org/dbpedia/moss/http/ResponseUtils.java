package org.dbpedia.moss.http;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.Response;

public final class ResponseUtils {

    private ResponseUtils() {}

    public static Response notAcceptable() {
        return Response.status(Response.Status.NOT_ACCEPTABLE)
                .entity("{\"message\":\"Requested content type not supported\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"message\":\"" + escapeJson(message) + "\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"message\":\"" + escapeJson(message) + "\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response conflict(String message) {
        return Response.status(Response.Status.CONFLICT)
                .entity("{\"message\":\"" + escapeJson(message) + "\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response serverError(String message) {
        return Response.serverError()
                .entity("{\"message\":\"" + escapeJson(message) + "\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response serviceUnavailable(String message) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("{\"message\":\"" + escapeJson(message) + "\"}")
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static Response noContent() {
        return Response.noContent().build();
    }

    public static Response textOk(String content, String mediaType) {
        return Response.ok(content, mediaType).build();
    }

    public static Response halOk(ObjectNode body, List<HateoasLink> links) {
        HttpUtils.addHateoasLinks(body, links);
        Response.ResponseBuilder builder = Response.ok(body, HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
        addLinkHeaders(builder, links);
        return builder.build();
    }

    public static Response htmlOk(String html) {
        return Response.ok(html, HttpConstants.MediaTypes.TEXT_HTML).build();
    }

    public static Response jsonOk(Object entity) {
        return Response.ok(entity, HttpConstants.MediaTypes.APPLICATION_JSON).build();
    }

    public static Response created(String entity, String mediaType) {
        return Response.status(Response.Status.CREATED).entity(entity).type(mediaType).build();
    }

    public static Response createdJson(Object entity) {
        return Response.status(Response.Status.CREATED)
                .entity(entity)
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .build();
    }

    public static void addLinkHeaders(Response.ResponseBuilder builder, List<HateoasLink> links) {
        for (HateoasLink link : links) {
            StringBuilder sb = new StringBuilder();
            sb.append("<").append(link.getHref()).append(">; rel=\"").append(link.getRel()).append("\"");
            if (link.getType() != null) {
                sb.append("; type=\"").append(link.getType()).append("\"");
            }
            if (link.isTemplated()) {
                sb.append("; templated=true");
            }
            builder.header(HttpConstants.Headers.LINK, sb.toString());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
