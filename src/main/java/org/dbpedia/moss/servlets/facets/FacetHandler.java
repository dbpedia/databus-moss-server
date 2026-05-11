package org.dbpedia.moss.servlets.facets;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FacetHandler {

    private final FacetStore store = new FacetStore(MossConfiguration.get().getFacetDirectory().toPath());

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public void listFacets(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<MossFacet> facets = store.listFacets();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/facets"),
                new HateoasLink("create", "/facets"),
                new HateoasLink("alternate", "/facets", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/facets", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {

                    ObjectNode result = getHalJsonList(facets);
                    HttpUtils.addHateoasLinks(result, links);

                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(result));
                    return;
                }

                case HttpConstants.MediaTypes.TEXT_HTML -> {

                    ObjectNode result = getHalJsonList(facets);
                    HttpUtils.addHateoasLinks(result, links);

                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);

                    String html = HttpUtils.getHtmlWrappedJson("facets", result);
                    resp.getWriter().write(html);
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    private ObjectNode getHalJsonList(List<MossFacet> facets) {
        ObjectNode result = jsonMapper.createObjectNode();
        ArrayNode items = jsonMapper.createArrayNode();

        for (MossFacet f : facets) {
            ObjectNode node = jsonMapper.createObjectNode();
            node.put("id", f.getId());
            node.put("label", f.getLabel());
            node.put("predicate", f.getPredicate());
            node.put("sortOrder", f.getSortOrder());

            ObjectNode links = jsonMapper.createObjectNode();
            links.set("self", jsonMapper.createObjectNode().put("href", "/facets/" + f.getId()));
            node.set("_links", links);

            items.add(node);
        }

        ObjectNode embedded = jsonMapper.createObjectNode();
        embedded.set("facets", items);
        result.set("_embedded", embedded);

        return result;
    }

    public void getFacet(HttpServletRequest req, HttpServletResponse resp, String id) throws IOException {
        Optional<MossFacet> facet = store.loadFacet(id);

        if (facet.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Facet not found: " + id);
            return;
        }

        ObjectNode body = jsonMapper.createObjectNode();
        facet.get().toJson(body);

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/facets/" + id),
                new HateoasLink("list", "/facets"),
                new HateoasLink("alternate", "/facets/" + id, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/facets/" + id, false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {

                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(body));
                    return;
                }

                case HttpConstants.MediaTypes.TEXT_HTML -> {

                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);

                    String html = HttpUtils.getHtmlWrappedJson(id, body);
                    resp.getWriter().write(html);
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    public void createFacet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        MossFacet facet = yamlMapper.readValue(req.getReader(), MossFacet.class);

        if (facet.getId() == null || facet.getId().isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Facet id is required");
            return;
        }

        if (store.loadFacet(facet.getId()).isPresent()) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, "Facet already exists");
            return;
        }

        store.saveFacet(facet);

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_JSON);
        resp.getWriter().write(jsonMapper.writeValueAsString(facet));
    }

    public void updateFacet(HttpServletRequest req, HttpServletResponse resp, String id) throws IOException {
        if (store.loadFacet(id).isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Facet not found: " + id);
            return;
        }

        MossFacet facet = yamlMapper.readValue(req.getReader(), MossFacet.class);
        facet.setId(id);

        store.saveFacet(facet);

        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_JSON);
        resp.getWriter().write(jsonMapper.writeValueAsString(facet));
    }

    public void deleteFacet(HttpServletRequest req, HttpServletResponse resp, String id) throws IOException {
        if (!store.deleteFacet(id)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Facet not found: " + id);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
