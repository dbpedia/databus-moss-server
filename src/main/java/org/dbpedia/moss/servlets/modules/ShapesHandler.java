package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ShapesHandler implements ISubResourceHandler {

    private static final String SHAPES_FILE = "shapes.ttl";
    private static final String CONTENT_TURTLE = "text/turtle";

    private final ModuleStore store;

    public ShapesHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<String> content = store.loadSubResource(moduleId, SHAPES_FILE);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Shapes not found for module: " + moduleId);
            return;
        }
        resp.setContentType(CONTENT_TURTLE);
        resp.getWriter().write(content.get());
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(moduleId, SHAPES_FILE, body);
        resp.setContentType(CONTENT_TURTLE);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteSubResource(moduleId, SHAPES_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Shapes not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
