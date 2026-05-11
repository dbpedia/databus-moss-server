package org.dbpedia.moss.servlets.terminologies;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.servlets.modules.ISubResourceHandler;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndexerQueryHandler implements ISubResourceHandler {

    public static final String INDEXER_FILE = "indexer.sparql";
    private static final String CONTENT_SPARQL = "application/sparql-query";

    private final TerminologyStore store;
    private final Consumer<String> changedHandler;

    public IndexerQueryHandler(Consumer<String> changedHandler) {
        this.changedHandler = changedHandler;
        store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<String> contentOpt = store.loadSubResource(terminologyId, INDEXER_FILE);
        if (contentOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Indexer query not found for terminology: " + terminologyId);
            return;
        }
        String content = contentOpt.get();

        // Link headers
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + "/indexer-query>; rel=\"self\"");
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + ">; rel=\"terminology\"");
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + "/indexer-query>; rel=\"alternate\"; type=\"" + HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY + "\"");
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + "/indexer-query>; rel=\"update\"; type=\"" + HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY + "\"");
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + "/indexer-query>; rel=\"delete\"");
        resp.addHeader(HttpConstants.Headers.LINK,
                "</terminologies/" + terminologyId + "/indexer-query>; rel=\"alternate\"; type=\"" + HttpConstants.MediaTypes.TEXT_HTML + "\"");

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedText("Indexer Query: " + terminologyId, content);
                    resp.getWriter().write(html);
                    return;
                }

                default -> {
                    if (type.equalsIgnoreCase(HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY)
                            || type.equals("*/*")) {
                        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY);
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().write(content);
                        return;
                    }
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(terminologyId, INDEXER_FILE, body);

        if (changedHandler != null) {
            changedHandler.accept(terminologyId);
        }

        resp.setContentType(CONTENT_SPARQL);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        boolean deleted = store.deleteSubResource(terminologyId, INDEXER_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Indexer query not found for terminology: " + terminologyId);
            return;
        }

        if (changedHandler != null) {
            changedHandler.accept(terminologyId);
        }

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
