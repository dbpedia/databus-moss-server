package org.dbpedia.moss.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class HttpUtils {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static String getHtmlWrappedText(String title, String content) {
        return "<html><head><title>" + escapeHtml(title) + "</title></head><body>"
                + "<pre>" + escapeHtml(content) + "</pre>"
                + "</body></html>";
    }

    private HttpUtils() {
    }

    public static String getHtmlWrappedJson(String title, ObjectNode result) throws JsonProcessingException {
        String jsonString = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        return "<html><head><title>" + escapeHtml(title) + "</title></head><body>"
                + "<pre>" + escapeHtml(jsonString) + "</pre>"
                + "</body></html>";
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static List<String> getAcceptedMediaTypes(HttpServletRequest req) {
        String acceptHeader = req.getHeader(HttpConstants.Headers.ACCEPT);
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return List.of("*/*");
        }

        return Arrays.stream(acceptHeader.split(","))
                .map(s -> s.split(";")[0].trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static void addHateoasLinks(ObjectNode node, List<HateoasLink> links) {
        ObjectNode linksNode = jsonMapper.createObjectNode();

        for (HateoasLink link : links) {
            ObjectNode linkNode = jsonMapper.createObjectNode();
            linkNode.put("href", link.getHref());
            if (link.isTemplated()) {
                linkNode.put("templated", true);
            }
            if (link.getType() != null) {
                linkNode.put("type", link.getType());
            }

            if (linksNode.has(link.getRel())) {
                if (linksNode.get(link.getRel()).isArray()) {
                    ((ArrayNode) linksNode.get(link.getRel())).add(linkNode);
                } else {
                    ArrayNode arr = jsonMapper.createArrayNode();
                    arr.add(linksNode.get(link.getRel()));
                    arr.add(linkNode);
                    linksNode.set(link.getRel(), arr);
                }
            } else {
                linksNode.set(link.getRel(), linkNode);
            }
        }

        node.set("_links", linksNode);
    }

    public static void addHateoasLinks(HttpServletResponse resp, List<HateoasLink> links) {
        for (HateoasLink link : links) {
            StringBuilder sb = new StringBuilder();
            sb.append("<").append(link.getHref()).append(">; rel=\"").append(link.getRel()).append("\"");
            if (link.getType() != null) {
                sb.append("; type=\"").append(link.getType()).append("\"");
            }
            if (link.isTemplated()) {
                sb.append("; templated=true");
            }
            resp.addHeader(HttpConstants.Headers.LINK, sb.toString());
        }
    }
}
