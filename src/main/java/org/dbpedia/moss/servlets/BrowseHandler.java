package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BrowseHandler {

    private final String gstoreBaseUrl;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public BrowseHandler(String gstoreBaseUrl) {
        this.gstoreBaseUrl = gstoreBaseUrl;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String targetUrl = buildGstoreUrl(req);

        String halJson;
        try {
            halJson = fetchHalJson(targetUrl);
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to fetch from gstore: " + e.getMessage());
            return;
        }

        ObjectNode halNode = (ObjectNode) jsonMapper.readTree(halJson);
        updateEmbeddedHAL(halNode);

        String requestURI = req.getRequestURI();
        List<HateoasLink> links = List.of(
                new HateoasLink("self", requestURI),
                new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.TEXT_HTML),
                new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("browse", navigateUp(requestURI), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON)
        );

        HttpUtils.addHateoasLinks(halNode, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        boolean htmlRequested = acceptedTypes.stream().anyMatch(t -> t.equals(HttpConstants.MediaTypes.TEXT_HTML));

        if (htmlRequested) {
            resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
            resp.setStatus(HttpServletResponse.SC_OK);
            String html = HttpUtils.getHtmlWrappedJson("browse", halNode);
            resp.getWriter().write(html);
        } else {
            resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(jsonMapper.writeValueAsString(halNode));
        }
    }

    private String fetchHalJson(String targetUrl) throws IOException {
        URI uri = URI.create(targetUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
        connection.setDoInput(true);

        connection.connect();

        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private String buildGstoreUrl(HttpServletRequest req) {
        String path = req.getPathInfo();

        if(path == null) {
            path = "";
        }
        
        return gstoreBaseUrl + "/file/header" + path;
    }

    private String navigateUp(String requestURI) {
        int lastSlash = requestURI.lastIndexOf('/');
        return (lastSlash > 0) ? requestURI.substring(0, lastSlash) : "/";
    }

    public void updateEmbeddedHAL(ObjectNode halNode) {
        ArrayNode items = getEmbeddedItems(halNode);
        if (items == null) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            ObjectNode item = (ObjectNode) items.get(i);

            ObjectNode self = (ObjectNode) item.path("_links").path("self");
            if (self.has("href")) {
                String href = self.get("href").asText();

                if ("file".equals(item.path("type").asText())) {
                    // Convert file -> entry and remove file extension
                    item.put("type", "entry");
                    int lastDot = href.lastIndexOf('.');
                    if (lastDot > 0) {
                        href = href.substring(0, lastDot);
                    }

                    href = href.replace("/file/header", "/entries");
                    self.put("href", href);

                    // Update name to match href (last segment)
                    item.put("name", href.substring(href.lastIndexOf('/') + 1));
                } else {
                    // For folders or other items, only fix the href
                    href = href.replace("/file/header", "/entries");
                    self.put("href", href);
                }
            }
        }
    }

    private ArrayNode getEmbeddedItems(ObjectNode halNode) {
        if (!halNode.has("_embedded")) {
            return null;
        }
        ObjectNode embedded = halNode.get("_embedded").isObject() ? (ObjectNode) halNode.get("_embedded") : null;
        if (embedded == null || !embedded.has("items")) {
            return null;
        }
        return embedded.get("items").isArray() ? (ArrayNode) embedded.get("items") : null;
    }
}
