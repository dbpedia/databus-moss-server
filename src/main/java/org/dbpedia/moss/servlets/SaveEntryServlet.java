package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.indexer.MossEntryHeader;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFParseException;
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
public class SaveEntryServlet extends HttpServlet {

    /**
     *
     */
    private static final long serialVersionUID = 102831973239L;

    final static Logger logger = LoggerFactory.getLogger(SaveEntryServlet.class);

    private final IndexerManager indexerManager;

    private final UserDatabaseManager userDatabaseManager;

    private final ModuleStore store;

    public SaveEntryServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;

        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void init() throws ServletException {
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        logger.info("Received save entry request.");
        long totalStart = System.currentTimeMillis();
        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_JSON);

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String requestBaseURL = ENV.MOSS_BASE_URL;

            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang contentTypeLanguage = MossUtils.getContentTypeLang(req);

            Model dataModel = ModelFactory.createDefaultModel();

            try (InputStream rdfInput = new ByteArrayInputStream(rdfString.getBytes())) {
                try {
                    RDFDataMgr.read(dataModel, rdfInput, contentTypeLanguage);
                } catch (RiotException e) {
                    logger.error("RDF syntax error", e);
                    throw new RDFParseException("Failed to parse RDF: " + e.getMessage(), e);
                }
            }

            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String moduleId = req.getParameter("module");

            logger.info("New write request: {}, {}, {}", req.getRequestURL(), moduleId, resource);

            var moduleOpt = store.loadModule(moduleId);

            if (moduleOpt.isEmpty()) {
                logger.error("Module not found: {}", moduleId);
                throw new RDFParseException("Module not found: " + moduleId);
            }

            var module = moduleOpt.get();
            Lang moduleLanguage = RDFLanguages.contentTypeToLang(module.getLanguage());

            String entryURI = MossUtils.getEntryUri(requestBaseURL, resource, module.getId());
            String headerDocumentPath = MossUtils.getHeaderStoragePath(resource, module.getId(), Lang.JSONLD);
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath);
            String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);

            MossEntryHeader header = MossEntryHeader.fromModel(entryURI, headerDocument.readModel(Lang.JSONLD), logger);
            header.setModifiedTime(currentTime);
            header.setModuleURI(module.getURI());
            header.setDatabusResourceURI(resource);
            header.setContentGraphURI(MossUtils.getContentGraphUri(requestBaseURL, resource, module.getId(), moduleLanguage));
            header.setLastModifiedBy(userInfo.getUsername());

            Model combinedModel = ModelFactory.createDefaultModel();
            combinedModel.add(dataModel);
            combinedModel.add(header.toModel());

            try {
                doShaclValidation(combinedModel, module);
            } catch (ValidationException e) {
                logger.error("SHACL validation failed", e);
                throw new RDFParseException("SHACL validation failure: " + e.getMessage(), e);
            }

            if (contentTypeLanguage != moduleLanguage) {
                logger.warn("Input RDF language {} is different from module language {}. Automatic conversion will occur.",
                        contentTypeLanguage.toLongString(), moduleLanguage.toLongString());

                StringWriter out = new StringWriter();
                RDFDataMgr.write(out, dataModel, moduleLanguage);
                rdfString = out.toString();
            }

            logger.info("Resource: {}", resource);
            logger.info("Entry URI: {}", entryURI);
            logger.info("Storage Language: {}", moduleLanguage);

            long headerStart = System.currentTimeMillis();
            headerDocument.writeModel(header.toModel(), Lang.JSONLD);
            long headerEnd = System.currentTimeMillis();

            long contentStart = System.currentTimeMillis();
            String contentDocumentPath = MossUtils.getContentStoragePath(resource, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
            contentDocument.writeDocument(rdfString, moduleLanguage);
            long contentEnd = System.currentTimeMillis();

            long totalEnd = System.currentTimeMillis();

            indexerManager.updateResource(entryURI, moduleId);

            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("message", "Success");
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, module.getId(), moduleLanguage));

            logger.info("Stored: {}", header.getURI());

            long totalTime = totalEnd - totalStart;
            long headerTime = headerEnd - headerStart;
            long contentTime = contentEnd - contentStart;
            long remainingTime = totalTime - headerTime - contentTime;

            double headerPct = totalTime > 0 ? (headerTime * 100.0) / totalTime : 0.0;
            double contentPct = totalTime > 0 ? (contentTime * 100.0) / totalTime : 0.0;
            double remainingPct = totalTime > 0 ? (remainingTime * 100.0) / totalTime : 0.0;

            logger.info("Request profiling: total={}ms | header={}ms ({}%) | content={}ms ({}%) | remaining={}ms ({}%)",
                    totalTime,
                    headerTime, String.format("%.2f", headerPct),
                    contentTime, String.format("%.2f", contentPct),
                    remainingTime, String.format("%.2f", remainingPct));

            resp.setStatus(200);
            resp.getWriter().write(objectMapper.writeValueAsString(jsonResponse));

        } catch (RDFParseException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Bad Request");
            error.put("message", e.getMessage());
            resp.setStatus(400);
            resp.getWriter().write(objectMapper.writeValueAsString(error));

        } catch (Exception e) {
            logger.error("Unexpected exception caught", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", e.getMessage());
            resp.setStatus(500);
            resp.getWriter().write(objectMapper.writeValueAsString(error));
        }
    }

    private void doShaclValidation(Model model, MossModule module) throws Exception {

        // Load the SHACL shape model from the file path provided by the layer
        var loadShapesResult = store.loadSubResource(module.getId(), "shapes.ttl");

        if (loadShapesResult.isEmpty()) {
            return;
        }

        var shaclString = loadShapesResult.get();

        Model shaclModel = ModelFactory.createDefaultModel();

        try {
            RDFParser.create()
                    .source(new StringReader(shaclString))
                    .lang(Lang.TURTLE)
                    .parse(shaclModel);
        } catch (RiotException e) {
            throw new ValidationException("Unable to parse SHACL for module: " + e.getMessage());
        }

        // Validate the RDF data model against the SHACL shape model
        ValidationReport report = ShaclValidator.get().validate(shaclModel.getGraph(), model.getGraph());

        // Check if validation passed or failed
        if (!report.conforms()) {
            // Validation failed: throw an exception or handle the report as needed
            throw new ValidationException("RDF data does not conform to SHACL shapes of layer " + module.getId()
                    + ": " + report.getEntries().toString());
        } else {
            logger.debug("Validation successful: RDF data conforms to SHACL shapes.");
        }
    }

}
