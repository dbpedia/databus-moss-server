package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.dbpedia.moss.config.LookupIndexer;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.MossDatasetUtils;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class IndexerPreviewServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(IndexerPreviewServlet.class);

    private final ModuleStore store;
    private final UserDatabaseManager userDatabaseManager;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public IndexerPreviewServlet(UserDatabaseManager userDatabaseManager) {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
        yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);

            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang contentLang = MossUtils.getContentTypeLang(req);

            Model contentModel = ModelFactory.createDefaultModel();
            try (InputStream in = new ByteArrayInputStream(rdfString.getBytes())) {
                RDFDataMgr.read(contentModel, in, contentLang);
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

            Dataset dataset = MossDatasetUtils.createEntryDataset(
                    module, resourceUri, userInfo.getUsername(), contentModel);

            System.out.println("===== Dataset N-Quads Dump =====");
            RDFDataMgr.write(System.out, dataset, RDFFormat.NQUADS);
            System.out.println("===== End Dataset Dump =====");

            var indexerOpt = store.loadSubResource(module.getId(), "indexer.yml");
            if (indexerOpt.isEmpty()) {
                logger.warn("No indexer.yaml found for module {}", module.getId());
                writeJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "No indexer configuration found");
                return;
            }

            LookupIndexer indexer = yamlMapper.readValue(indexerOpt.get(), LookupIndexer.class);
            Map<String, Object> indexerResults = runIndexerQueries(dataset, indexer);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getWriter().write(jsonMapper.writeValueAsString(indexerResults));

        } catch (Exception e) {
            logger.error("Indexer query error", e);
            writeJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

    private Map<String, Object> runIndexerQueries(Dataset dataset, LookupIndexer indexer) {
        Map<String, Object> results = new HashMap<>();
        if (indexer.getIndexFields() == null) {
            return results;
        }

        for (LookupIndexer.IndexField field : indexer.getIndexFields()) {
            String query = field.getQuery();
            String fieldName = field.getFieldName();
            List<Map<String, String>> bindings = new ArrayList<>();

            try (QueryExecution qe = QueryExecutionFactory.create(query, dataset)) {
                ResultSet rs = qe.execSelect();
                while (rs.hasNext()) {
                    QuerySolution sol = rs.next();
                    String key = sol.contains(field.getDocumentVariable()) ? sol.get(field.getDocumentVariable()).toString() : null;
                    String value = sol.contains(field.getFieldName()) ? sol.get(field.getFieldName()).toString() : null;
                    Map<String, String> binding = new HashMap<>();
                    binding.put("key", key);
                    binding.put("value", value);
                    bindings.add(binding);
                }
            } catch (Exception e) {
               
            }

            Map<String, Object> fieldObj = new HashMap<>();
            fieldObj.put("bindings", bindings);
            results.put(fieldName, fieldObj);
        }

        return results;
    }

    private void writeJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        String json = String.format("{\"message\": \"%s\", \"status\": %d}", message.replace("\"", "\\\""), status);
        resp.getWriter().write(json);
    }
}
