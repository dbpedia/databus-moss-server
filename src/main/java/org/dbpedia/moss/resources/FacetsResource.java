package org.dbpedia.moss.resources;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.generated.api.FacetsApi;
import org.dbpedia.moss.servlets.facets.FacetStore;
import org.dbpedia.moss.servlets.facets.MossFacet;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;
import org.dbpedia.moss.utils.ResponseUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class FacetsResource implements FacetsApi {

    @Context
    private HttpServletRequest request;

    private final FacetStore store = new FacetStore(MossConfiguration.get().getFacetDirectory().toPath());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Response listFacets() {
        List<MossFacet> facets;
        try {
            facets = store.listFacets();
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to list facets: " + e.getMessage());
        }

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/facets"),
                new HateoasLink("create", "/facets"),
                new HateoasLink("alternate", "/facets", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/facets", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode result = getHalJsonList(facets);
                    return ResponseUtils.halOk(result, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode result = getHalJsonList(facets);
                    HttpUtils.addHateoasLinks(result, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson("facets", result),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render facets: " + e.getMessage());
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
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

    @Override
    public Response getFacet(String facetId) {
        Optional<MossFacet> facet;
        try {
            facet = store.loadFacet(facetId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load facet: " + e.getMessage());
        }

        if (facet.isEmpty()) {
            return ResponseUtils.notFound("Facet not found: " + facetId);
        }

        ObjectNode body = jsonMapper.createObjectNode();
        facet.get().toJson(body);

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/facets/" + facetId),
                new HateoasLink("list", "/facets"),
                new HateoasLink("alternate", "/facets/" + facetId, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/facets/" + facetId, false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    return ResponseUtils.halOk(body, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    HttpUtils.addHateoasLinks(body, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson(facetId, body),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render facet: " + e.getMessage());
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response createFacet(String body) {
        MossFacet facet;
        try {
            facet = yamlMapper.readValue(body, MossFacet.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid facet payload: " + e.getMessage());
        }

        if (facet.getId() == null || facet.getId().isBlank()) {
            return ResponseUtils.badRequest("Facet id is required");
        }

        try {
            if (store.loadFacet(facet.getId()).isPresent()) {
                return ResponseUtils.conflict("Facet already exists");
            }
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load facet: " + e.getMessage());
        }

        try {
            store.saveFacet(facet);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save facet: " + e.getMessage());
        }

        return ResponseUtils.createdJson(facet);
    }

    @Override
    public Response updateFacet(String facetId, String body) {
        try {
            if (store.loadFacet(facetId).isEmpty()) {
                return ResponseUtils.notFound("Facet not found: " + facetId);
            }
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load facet: " + e.getMessage());
        }

        MossFacet facet;
        try {
            facet = yamlMapper.readValue(body, MossFacet.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid facet payload: " + e.getMessage());
        }
        facet.setId(facetId);

        try {
            store.saveFacet(facet);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to update facet: " + e.getMessage());
        }

        return ResponseUtils.jsonOk(facet);
    }

    @Override
    public Response deleteFacet(String facetId) {
        boolean deleted;
        try {
            deleted = store.deleteFacet(facetId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete facet: " + e.getMessage());
        }
        if (!deleted) {
            return ResponseUtils.notFound("Facet not found: " + facetId);
        }
        return ResponseUtils.noContent();
    }
}
