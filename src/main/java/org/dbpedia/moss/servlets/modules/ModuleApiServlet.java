package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.indexer.IndexerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ModuleApiServlet extends HttpServlet implements IIndexerChangedHandler {

    private final ModuleHandler moduleHandler;

    private final IndexerManager indexerManager;
    // Each entry: regex -> handler
    private final List<RegexHandler> subResourceHandlers;

    private final ModuleStore moduleStore;

    final static Logger logger = LoggerFactory.getLogger(ModuleApiServlet.class);

    public ModuleApiServlet(IndexerManager indexerManager) {
        this.indexerManager = indexerManager;
        moduleHandler = new ModuleHandler(this);
        moduleStore = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
        subResourceHandlers = List.of(
                new RegexHandler(Pattern.compile("^shapes$"), new ShapesHandler()),
                new RegexHandler(Pattern.compile("^context$"), new ContextHandler()),
                new RegexHandler(Pattern.compile("^indexer$"), new IndexerHandler(this)),
                new RegexHandler(Pattern.compile("^template$"), new TemplateHandler())
        );
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            moduleHandler.listModules(req, resp);
            return;
        }

        if (parts.subResource == null) {
            moduleHandler.getModule(req, resp, parts.moduleId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.get(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST allowed only on module collection");
            return;
        }

        moduleHandler.createModule(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PUT requires moduleId");
            return;
        }

        if (parts.subResource == null) {
            moduleHandler.updateModule(req, resp, parts.moduleId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.update(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DELETE requires moduleId");
            return;
        }

        if (parts.subResource == null) {
            moduleHandler.deleteModule(req, resp, parts.moduleId);
            return;
        }

        ISubResourceHandler handler = resolveHandler(parts.subResource);
        if (handler != null) {
            handler.delete(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    @Override
    public void onIndexerChanged(String moduleId) {
        try {
            Optional<String> indexerResource = moduleStore.loadSubResource(moduleId,
                    IndexerHandler.INDEXER_FILE);

            if (indexerResource.isEmpty()) {
                indexerManager.removeIndexGroup(moduleId);
            } else {
                indexerManager.createOrUpdateIndexGroup(moduleId, indexerResource.get());
            }

        } catch (IOException e) {
            logger.error(e.getMessage());
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

        String moduleId = segments[1];
        String subResource = segments.length > 2 ? segments[2] : null;
        return new PathParts(moduleId, subResource);
    }

    private static class PathParts {

        final String moduleId;
        final String subResource;

        PathParts(String moduleId, String subResource) {
            this.moduleId = moduleId;
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
}
