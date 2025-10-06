package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
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
public class MetadataWriteServlet extends HttpServlet {

    /**
     *
     */
    private static final long serialVersionUID = 102831973239L;

    final static Logger logger = LoggerFactory.getLogger(MetadataWriteServlet.class);

    private final IndexerManager indexerManager;

    private final UserDatabaseManager userDatabaseManager;

    private final ModuleStore store;

    public MetadataWriteServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
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

            Model dataModel = ModelFactory.createDefaultModel();
            try (InputStream rdfInput = new ByteArrayInputStream(rdfString.getBytes())) {
                RDFDataMgr.read(dataModel, rdfInput, contentTypeLanguage);
            }

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

            // SHACL test this!
            doShaclValidation(combinedModel, module);

            // Layer language *should* be the same as the request language, but doesn't have to
            if (contentTypeLanguage != moduleLanguage) {
                // Log warning
                logger.warn("Input RDF language " + contentTypeLanguage.toLongString()
                        + " is different from the RDF language defined for this layer " + moduleLanguage.toLongString()
                        + ". Automatic conversion will happen.");

                StringWriter out = new StringWriter();
                RDFDataMgr.write(out, dataModel, moduleLanguage);
                rdfString = out.toString();
            }

            logger.info("Resource: {}", resource);
            logger.info("Entry URI: {}", entryURI);
            logger.info("Storage Language: {}", moduleLanguage);

            // validateResourceForLayer(resource, layer);
            // Get the gstore resource for the header 
            headerDocument.writeModel(header.toModel(), Lang.JSONLD);

            // Get the gstore resource for the content
            String contentDocumentPath = MossUtils.getContentStoragePath(resource, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
            contentDocument.writeDocument(rdfString, moduleLanguage);

            indexerManager.updateResource(entryURI, moduleId);

            // Create JSON response
            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, module.getId(), moduleLanguage));

            // Convert to JSON and send response
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponseString = objectMapper.writeValueAsString(jsonResponse);

            resp.setContentType("application/json");
            resp.setStatus(200);
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
