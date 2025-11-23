package org.dbpedia.moss.servlets.terminologies;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.servlets.modules.ISubResourceHandler;
import org.dbpedia.moss.utils.LookupServer;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TerminologyServlet extends HttpServlet {

    private final TerminologyHandler terminologyHandler;

    // Each entry: regex -> handler
    private final List<RegexHandler> subResourceHandlers;

    private final TerminologyStore store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());

    private final DataHandler dataHandler;

    private final IndexerQueryHandler indexQueryHandler;

    // final static Logger logger = LoggerFactory.getLogger(TerminologyApiServlet.class);
    public TerminologyServlet() {
        terminologyHandler = new TerminologyHandler();
        dataHandler = new DataHandler(this::onTerminologyChanged);
        indexQueryHandler = new IndexerQueryHandler(this::onTerminologyChanged);
        SearchHandler searchHandler = new SearchHandler();

        subResourceHandlers = List.of(
                new RegexHandler(Pattern.compile("^indexer-query$"), indexQueryHandler),
                new RegexHandler(Pattern.compile("^data$"), dataHandler),
                new RegexHandler(Pattern.compile("^search$"), searchHandler)
        );
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.terminologyId == null) {
            terminologyHandler.listTerminologies(req, resp);
            return;
        }

        if (parts.subResource == null) {
            terminologyHandler.getTerminology(req, resp, parts.terminologyId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.get(req, resp, parts.terminologyId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.terminologyId != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST allowed only on terminology collection");
            return;
        }

        terminologyHandler.createTerminology(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.terminologyId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PUT requires terminologyId");
            return;
        }

        if (parts.subResource == null) {
            terminologyHandler.updateTerminology(req, resp, parts.terminologyId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.update(req, resp, parts.terminologyId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.terminologyId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DELETE requires terminologyId");
            return;
        }

        if (parts.subResource == null) {
            terminologyHandler.deleteTerminology(req, resp, parts.terminologyId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.delete(req, resp, parts.terminologyId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    private ISubResourceHandler resolveHandler(String subResource) {
        return subResourceHandlers.stream()
                .filter(entry -> entry.pattern.matcher(subResource).matches())
                .map(entry -> entry.handler)
                .findFirst()
                .orElse(null);
    }

    private PathParts parsePath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            return new PathParts(null, null);
        }

        String[] segments = pathInfo.split("/");
        if (segments.length < 2) {
            return new PathParts(null, null);
        }

        String terminologyId = segments[1];
        String subResource = segments.length > 2 ? segments[2] : null;
        return new PathParts(terminologyId, subResource);
    }

    private static class PathParts {

        final String terminologyId;
        final String subResource;

        PathParts(String terminologyId, String subResource) {
            this.terminologyId = terminologyId;
            this.subResource = subResource;
        }
    }

    private static class RegexHandler {

        final Pattern pattern;
        final ISubResourceHandler handler;

        RegexHandler(Pattern pattern, ISubResourceHandler handler) {
            this.pattern = pattern;
            this.handler = handler;
        }
    }

    private void onTerminologyChanged(String terminologyId) {

        try {
            var terminologyResponse = store.loadTerminology(terminologyId);

            if (terminologyResponse.isPresent()) {

                var terminology = terminologyResponse.get();
                
                try (LookupServer lookupServer = new LookupServer(terminology.getIndexPath())) {
                    lookupServer.index(terminology.getDataModel(), terminology.getIndexerQuery());
                }

            }

        } catch (IOException e) {

        }
    }

}
