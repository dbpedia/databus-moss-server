package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.MossDatasetUtils;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MetadataValidationServlet extends HttpServlet {

    private static final long serialVersionUID = 102831973239L;
    private static final Logger logger = LoggerFactory.getLogger(MetadataValidationServlet.class);

    private final ModuleStore store;

    private final UserDatabaseManager userDatabaseManager;

    public MetadataValidationServlet(UserDatabaseManager userDatabaseManager) {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang contentLang = MossUtils.getContentTypeLang(req);

            Model contentModel = ModelFactory.createDefaultModel();
            try (InputStream rdfInput = new ByteArrayInputStream(rdfString.getBytes())) {
                RDFDataMgr.read(contentModel, rdfInput, contentLang);
            }

            String resourceUri = MossUtils.pruneSlashes(req.getParameter("resource"));
            String moduleId = req.getParameter("module");

            if (moduleId == null || moduleId.isEmpty()) {
                writeJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: module");
                return;
            }

            var moduleOpt = store.loadModule(moduleId);
            if (moduleOpt.isEmpty()) {
                writeJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
                return;
            }

            MossModule module = moduleOpt.get();

            Dataset dataset = MossDatasetUtils.createEntryDataset(module,
                    resourceUri, userInfo.getUsername(), contentModel);

            var mergedModel = dataset.getUnionModel();

            ValidationReport report = doShaclValidation(mergedModel, module);

            String acceptHeader = req.getHeader("Accept");
            Lang responseLang = RDFLanguages.contentTypeToLang(acceptHeader);
            if (responseLang == null) {
                responseLang = Lang.TURTLE;
            }

            resp.setStatus(report.conforms() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType(responseLang.getContentType().getContentTypeStr());
            RDFDataMgr.write(resp.getOutputStream(), report.getModel(), responseLang);

        } catch (Exception e) {
            logger.error("Validation error", e);
            writeJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    private void writeJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        String json = String.format("{\"message\": \"%s\", \"status\": %d}",
                message.replace("\"", "\\\""), status);
        resp.getWriter().write(json);
    }

    private ValidationReport doShaclValidation(Model dataModel, MossModule module) throws Exception {
        var loadShapesResult = store.loadSubResource(module.getId(), "shapes.ttl");
        if (loadShapesResult.isEmpty()) {
            logger.warn("No SHACL shapes found for module {}", module.getId());
            return ShaclValidator.get().validate(ModelFactory.createDefaultModel().getGraph(), dataModel.getGraph());
        }

        var shaclModel = MossDatasetUtils.parseShaclShapes(loadShapesResult.get());
        return ShaclValidator.get().validate(shaclModel.getGraph(), dataModel.getGraph());
    }
}
