package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndexerHandler implements ISubResourceHandler {

    private static final String INDEXER_FILE = "indexer.yml";
    private static final String CONTENT_YAML = "application/x-yaml";

    private final ModuleStore store;

    public IndexerHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<String> content = store.loadSubResource(moduleId, INDEXER_FILE);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Indexer not found for module: " + moduleId);
            return;
        }
        resp.setContentType(CONTENT_YAML);
        resp.getWriter().write(content.get());
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(moduleId, INDEXER_FILE, body);
        resp.setContentType(CONTENT_YAML);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteSubResource(moduleId, INDEXER_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Indexer not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
