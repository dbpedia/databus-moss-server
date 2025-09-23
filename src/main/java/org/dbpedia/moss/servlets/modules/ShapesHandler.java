package org.dbpedia.moss.servlets.modules;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ShapesHandler implements SubResourceHandler {

    private static final String CONTENT_TURTLE = "text/turtle";

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        resp.setContentType(CONTENT_TURTLE);
        resp.getWriter().write("# shapes for " + moduleId);
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        resp.setContentType(CONTENT_TURTLE);
        resp.getWriter().write("# updated shapes for " + moduleId);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) {
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
