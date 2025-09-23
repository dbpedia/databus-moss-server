package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet for handling CRUD operations on MOSS modules.
 * 
 * Each module may expose sub-resources in addition to its base metadata:
 *   - context.jsonld (JSON-LD context document)
 *   - shapes.ttl (SHACL shapes in Turtle)
 * 
 * Mapping:
 *   GET    /modules                -> list all modules
 *   POST   /modules                -> create a new module
 *   GET    /modules/{id}           -> get single module metadata (linked data resource)
 *   PUT    /modules/{id}           -> update a module
 *   DELETE /modules/{id}           -> delete a module
 * 
 *   GET    /modules/{id}/shapes.ttl       -> get shapes for module
 *   PUT    /modules/{id}/shapes.ttl       -> update shapes
 *   DELETE /modules/{id}/shapes.ttl       -> delete shapes
 * 
 *   GET    /modules/{id}/context.jsonld   -> get JSON-LD context
 *   PUT    /modules/{id}/context.jsonld   -> update JSON-LD context
 *   DELETE /modules/{id}/context.jsonld   -> delete JSON-LD context
 * 
 * Sub-resources are dispatched to dedicated handlers for modularity.
 */
public class ModulesServlet extends HttpServlet {

    // Constants for known sub-resource file names
    private static final String CONTEXTJSONLD = "context.jsonld";
    private static final String SHAPESTTL = "shapes.ttl";
    private static final String INDEXERYML = "indexer.yml";

    // Handler for module-level CRUD
    private final ModuleHandler moduleHandler = new ModuleHandler();

    // Registry of sub-resource handlers (keeps servlet thin)
    private final Map<String, ISubResourceHandler> subResourceMap = Map.of(
        SHAPESTTL, new ShapesHandler(), 
        CONTEXTJSONLD, new ContextHandler(),
        INDEXERYML, new IndexerHandler()
    );

    /**
     * Handles GET requests:
     * - /modules                  -> list modules
     * - /modules/{id}             -> single module metadata
     * - /modules/{id}/{subfile}   -> delegate to sub-resource handler
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            // No ID provided -> list all modules
            moduleHandler.listModules(req, resp);
            return;
        }

        if (parts.subResource == null) {
            // Only ID -> get module metadata
            moduleHandler.getModule(req, resp, parts.moduleId);
            return;
        }

        // Sub-resource present -> delegate if known
        ISubResourceHandler handler = subResourceMap.get(parts.subResource);
        if (handler != null) {
            handler.get(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    /**
     * Handles POST requests:
     * - /modules -> create a new module
     * 
     * POST on an existing module path is invalid.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId != null) {
            // POST only allowed on collection
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "POST allowed only on module collection");
            return;
        }

        moduleHandler.createModule(req, resp);
    }

    /**
     * Handles PUT requests:
     * - /modules/{id}             -> update a module
     * - /modules/{id}/{subfile}   -> update sub-resource
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "PUT requires moduleId");
            return;
        }

        if (parts.subResource == null) {
            // Update module itself
            moduleHandler.updateModule(req, resp, parts.moduleId);
            return;
        }

        // Update sub-resource if registered
        ISubResourceHandler handler = subResourceMap.get(parts.subResource);
        if (handler != null) {
            handler.update(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    /**
     * Handles DELETE requests:
     * - /modules/{id}             -> delete module
     * - /modules/{id}/{subfile}   -> delete sub-resource
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PathParts parts = parsePath(req);

        if (parts.moduleId == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "DELETE requires moduleId");
            return;
        }

        if (parts.subResource == null) {
            // Delete module
            moduleHandler.deleteModule(req, resp, parts.moduleId);
            return;
        }

        // Delete sub-resource if registered
        ISubResourceHandler handler = subResourceMap.get(parts.subResource);
        if (handler != null) {
            handler.delete(req, resp, parts.moduleId);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown subresource: " + parts.subResource);
        }
    }

    /**
     * Splits request path into moduleId and optional sub-resource name.
     * 
     * Examples:
     *   /              -> (null, null)
     *   /123           -> ("123", null)
     *   /123/shapes.ttl -> ("123", "shapes.ttl")
     */
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

    /**
     * Value object holding parsed path parts (moduleId and sub-resource).
     */
    private static class PathParts {
        final String moduleId;
        final String subResource;

        PathParts(String moduleId, String subResource) {
            this.moduleId = moduleId;
            this.subResource = subResource;
        }
    }
}
