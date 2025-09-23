package org.dbpedia.moss.servlets.modules;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ContextHandler implements SubResourceHandler {

    private static final String CONTENT_JSONLD = "application/ld+json";

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write("{\"@context\":{\"id\":\"" + moduleId + "\"}}");
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write("{\"@context\":{\"id\":\"" + moduleId + "\",\"updated\":true}}");
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) {
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
