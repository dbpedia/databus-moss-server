package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes to metadata documents Example:
 * /api/write?layerName=openenergy&resource=someurl
 */
public class DeleteEntryServlet extends HttpServlet {

    /**
     *
     */
    private static final long serialVersionUID = 102831973239L;

    final static Logger logger = LoggerFactory.getLogger(DeleteEntryServlet.class);

    private final IndexerManager indexerManager;

    private final UserDatabaseManager userDatabaseManager;

    private final ModuleStore store;

    public DeleteEntryServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;

        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void init() throws ServletException {
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            String requestBaseURL = ENV.MOSS_BASE_URL;
            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang contentTypeLanguage = MossUtils.getContentTypeLang(req);

            // The two important input parameters
            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String moduleId = req.getParameter("module");

            logger.info("New write request: {}, {}, {}", req.getRequestURL(), moduleId, resource);

            var moduleOpt = store.loadModule(moduleId);

            if (moduleOpt.isEmpty()) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
                return;
            }

            var module = moduleOpt.get();

            if (module == null) {
                throw new IllegalArgumentException("Specified module is unkown to this MOSS instance: " + moduleId);
            }

            Lang moduleLanguage = RDFLanguages.contentTypeToLang(module.getLanguage());

            String entryURI = MossUtils.getEntryUri(requestBaseURL, resource, module.getId());
            String headerDocumentPath = MossUtils.getHeaderStoragePath(resource, module.getId(), Lang.JSONLD);
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath, userInfo);


            logger.info("Resource: {}", resource);
            logger.info("Entry URI: {}", entryURI);
            logger.info("Storage Language: {}", moduleLanguage);

            // validateResourceForLayer(resource, layer);
            // Get the gstore resource for the header 
            int deletionResult = headerDocument.delete();

            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry header from database.");
            }

            // Get the gstore resource for the content
            String contentDocumentPath = MossUtils.getContentStoragePath(resource, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath, userInfo);

            deletionResult = contentDocument.delete();

            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry content from database.");
            }

            indexerManager.updateResource(entryURI, moduleId);

            // Create JSON response
            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("statusCode", "" + deletionResult);
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, module.getId(), moduleLanguage));

            // Convert to JSON and send response
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponseString = objectMapper.writeValueAsString(jsonResponse);

            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            resp.getWriter().write(jsonResponseString);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException caught", e);
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, e.getMessage());
        } catch (UnsupportedEncodingException | URISyntaxException | ValidationException | RiotException e) {
            logger.error("Client error caught", e);
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected exception caught", e);
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(500, e.getMessage());
        }
    }
}
