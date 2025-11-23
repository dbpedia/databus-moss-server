package org.dbpedia.moss.servlets.terminologies;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.servlets.modules.ISubResourceHandler;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;
import org.dbpedia.moss.utils.LookupServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SearchHandler implements ISubResourceHandler {

    private final TerminologyStore store;

    private static final String QUERY = "query";

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public SearchHandler() {
        this.store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> terminologyOpt = store.loadTerminology(terminologyId);
        if (terminologyOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }

        MossTerminology terminology = terminologyOpt.get();
        String query = req.getParameter(QUERY);

        ObjectNode docsNode = jsonMapper.createObjectNode();

        if (query != null && !query.isBlank()) {
            try (LookupServer server = new LookupServer(terminology.getIndexPath())) {
                List<LookupServer.SearchResult> results = server.search(query, 50);

                ArrayNode resultsArray = jsonMapper.createArrayNode();
                for (LookupServer.SearchResult r : results) {
                    ObjectNode obj = jsonMapper.createObjectNode();
                    obj.put("id", r.id());
                    ArrayNode labelsArray = jsonMapper.createArrayNode();
                    for (String label : r.label()) {
                        labelsArray.add(label);
                    }
                    obj.set("label", labelsArray);
                    obj.put("score", r.score());
                    resultsArray.add(obj);
                }
                docsNode.set("docs", resultsArray);
            }
        }

        // Define HATEOAS links once
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies/" + terminologyId + "/search"),
                new HateoasLink("terminology", "/terminologies/" + terminologyId),
                new HateoasLink("alternate", "/terminologies/" + terminologyId + "/search{?query}", true, HttpConstants.MediaTypes.TEXT_HTML)
        );

        // Add HAL JSON _links
        HttpUtils.addHateoasLinks(docsNode, links);

        // Add HTTP Link headers
        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedJson("Search results for " + terminologyId, docsNode);
                    resp.getWriter().write(html);
                    return;
                }
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(docsNode));
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Search is read-only");
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Search is read-only");
    }
}
