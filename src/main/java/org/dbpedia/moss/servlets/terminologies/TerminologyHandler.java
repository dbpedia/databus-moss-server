package org.dbpedia.moss.servlets.terminologies;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TerminologyHandler {

    private static final String CONTENT_JSONLD = "application/ld+json";

    private final TerminologyStore store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public void listTerminologies(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<MossTerminology> terminologies = store.listTerminologies();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies"),
                new HateoasLink("create", "/terminologies"),
                new HateoasLink("alternate", "/terminologies", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/terminologies", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );
        
        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode result = GetHALJsonList(terminologies);
                    HttpUtils.addHateoasLinks(result, links);
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(result));
                    return;
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode result = GetHALJsonList(terminologies);
                    HttpUtils.addHateoasLinks(result, links);
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedJson("terminologies", result);
                    resp.getWriter().write(html);
                    return;
                }
            }

            // No acceptable type found
            resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
        }
    }

    private ObjectNode GetHALJsonList(List<MossTerminology> terminologies) {
        // Collection-level HAL JSON
        ObjectNode result = jsonMapper.createObjectNode();
        ArrayNode items = jsonMapper.createArrayNode();
        for (MossTerminology t : terminologies) {
            ObjectNode node = jsonMapper.createObjectNode();
            node.put("id", t.getId());
            node.put("label", t.getLabel());
            node.put("language", t.getLanguage());

            ObjectNode links = jsonMapper.createObjectNode();
            links.set("self", jsonMapper.createObjectNode().put("href", "/terminologies/" + t.getId()));
            node.set("_links", links);

            items.add(node);
        }
        ObjectNode embedded = jsonMapper.createObjectNode();
        embedded.set("terminologies", items);
        result.set("_embedded", embedded);
        return result;
    }

    public void getTerminology(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> content = store.loadTerminology(terminologyId);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }

        MossTerminology terminology = content.get();

        // Build header links
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies/" + terminology.getId()),
                new HateoasLink("alternate", "/terminologies/" + terminology.getId(), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/terminologies/" + terminology.getId(), false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("data", "/terminologies/" + terminology.getId() + "/data", false, terminology.getLanguage()),
                new HateoasLink("indexer", "/terminologies/" + terminology.getId() + "/indexer-query", false, HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY),
                new HateoasLink("search", "/terminologies/" + terminology.getId() + "/search{?query}", true, null),
                new HateoasLink("list", "/terminologies"));

        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode body = terminology.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(body));
                    return;
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode body = terminology.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedJson(terminology.getId(), body);
                    resp.getWriter().write(html);
                    return;
                }
            }
        }

        // No accepted type matched
        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    public void createTerminology(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        MossTerminology terminology = yamlMapper.readValue(req.getReader(), MossTerminology.class);

        if (terminology.getId() == null || terminology.getId().isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Terminology ID must be provided");
            return;
        }

        terminology.setId(terminology.getId().trim());

        // Check for duplicate
        if (store.loadTerminology(terminology.getId()).isPresent()) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, "Terminology ID already exists");
            return;
        }

        try {
            store.saveTerminology(terminology);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save terminology: " + e.getMessage());
            return;
        }

        resp.setContentType(CONTENT_JSONLD);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(yamlMapper.writeValueAsString(terminology));
    }

    public void updateTerminology(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> existing = store.loadTerminology(terminologyId);
        if (existing.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }

        MossTerminology terminology = yamlMapper.readValue(req.getReader(), MossTerminology.class);
        terminology.setId(terminologyId);

        try {
            store.saveTerminology(terminology);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update terminology: " + e.getMessage());
            return;
        }

        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write(yamlMapper.writeValueAsString(terminology));
    }

    public void deleteTerminology(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        boolean deleted = store.deleteTerminology(terminologyId);

        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
