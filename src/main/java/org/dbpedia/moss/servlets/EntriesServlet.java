package org.dbpedia.moss.servlets;

import java.io.IOException;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.utils.ENV;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EntriesServlet extends HttpServlet {

    private final ResourceHandler resourceHandler;
    private final BrowseHandler browseHandler;

    private static final String ASK_TEMPLATE
            = "ASK WHERE { <%s> ?p ?o }";

    public EntriesServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        resourceHandler = new ResourceHandler(indexerManager, userDatabaseManager);
        browseHandler = new BrowseHandler(ENV.GSTORE_BASE_URL);
    }

    @Override
    public void init() throws ServletException {
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.sendError(404, "Entry path missing");
            return;
        }

        String resourceUri = ENV.MOSS_BASE_URL + "/entries" + pathInfo;

        if (resourceExists(resourceUri)) {
            resourceHandler.doDelete(req, resp);
        } else {
            resp.sendError(406, "Method Not Allowed");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        if (pathInfo == null) {
            pathInfo = "";
        }
        
        String resourceUri = ENV.MOSS_BASE_URL + "/entries" + pathInfo;

        if (resourceExists(resourceUri)) {
            resourceHandler.doGet(req, resp);
        } else {
            browseHandler.doGet(req, resp);
        }
    }

    private boolean resourceExists(String resourceUri) {
        String askQuery = String.format(ASK_TEMPLATE, resourceUri);
        try (RDFConnection conn = RDFConnectionRemote.service(ENV.STORE_SPARQL_ENDPOINT).build()) {
            return conn.queryAsk(askQuery);
        } catch (Exception e) {
            return false;
        }
    }
}
