package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ContextHandler implements ISubResourceHandler {

    private static final String CONTEXT_FILE = "context.jsonld";
    private static final String CONTENT_JSONLD = "application/ld+json";

    private final ModuleStore store;

    public ContextHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<String> content = store.loadSubResource(moduleId, CONTEXT_FILE);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Context not found for module: " + moduleId);
            return;
        }

        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write(content.get());
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(moduleId, CONTEXT_FILE, body);
        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteSubResource(moduleId, CONTEXT_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Context not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
