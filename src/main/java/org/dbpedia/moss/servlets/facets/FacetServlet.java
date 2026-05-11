package org.dbpedia.moss.servlets.facets;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FacetServlet extends HttpServlet {

    private final FacetHandler handler = new FacetHandler();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        
        String facetId = getIdFromPathInfo(req);

        if (facetId == null) {
            handler.listFacets(req, resp);
        } else {
            handler.getFacet(req, resp, facetId);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (getIdFromPath(req) != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST only allowed on facet collection");
            return;
        }
        handler.createFacet(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String facetId = getIdFromPath(req);
        if (facetId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PUT requires facetId");
            return;
        }
        handler.updateFacet(req, resp, facetId);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String facetId = getIdFromPath(req);
        if (facetId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DELETE requires facetId");
            return;
        }
        handler.deleteFacet(req, resp, facetId);
    }

    private String getIdFromPath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            return null;
        }
        String[] segments = pathInfo.split("/");
        return segments.length > 1 ? segments[1] : null;
    }

     private String getIdFromPathInfo(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            return null;
        }
        String[] segments = pathInfo.split("/");
        return segments.length > 1 ? segments[1] : null;
    }
}
