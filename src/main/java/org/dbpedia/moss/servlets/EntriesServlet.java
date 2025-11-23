package org.dbpedia.moss.servlets;

import java.io.IOException;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.dbpedia.moss.utils.ENV;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class EntriesServlet extends HttpServlet {

    private ResourceHandler resourceHandler;
    private BrowseHandler browseHandler;

    private static final String ASK_TEMPLATE
            = "ASK WHERE { <%s> ?p ?o }";

    @Override
    public void init() throws ServletException {
        resourceHandler = new ResourceHandler();
        browseHandler = new BrowseHandler(ENV.GSTORE_BASE_URL);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            resp.sendError(404, "Entry path missing");
            return;
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
