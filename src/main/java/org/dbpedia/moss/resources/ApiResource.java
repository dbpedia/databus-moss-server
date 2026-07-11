package org.dbpedia.moss.resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.db.APIKeyInfo;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.generated.api.ApiApi;
import org.dbpedia.moss.indexer.MossEntryHeader;
import org.dbpedia.moss.servlets.ValidationException;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.MossDatasetUtils;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.MutableServletRequest;
import org.dbpedia.moss.utils.RDFParseException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class ApiResource implements ApiApi {

    private static final Logger logger = LoggerFactory.getLogger(ApiResource.class);

    @Context
    private HttpServletRequest request;

    private final UserDatabaseManager userDatabaseManager;
    private final ModuleStore store;

    @Inject
    public ApiResource(UserDatabaseManager userDatabaseManager) {
        this.userDatabaseManager = userDatabaseManager;
        this.store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public Response saveEntry(String module, String resource, String body) {
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.TEXT_TURTLE)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return doSaveEntry(req);
    }

    @Override
    public Response deleteEntryByQuery(String module, String resource) {
        HttpServletRequest req = new MutableServletRequest(request)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return doDeleteEntry(req);
    }

    @Override
    public Response validateEntry(String module, String body, String resource) {
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.TEXT_TURTLE)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return doValidateEntry(req);
    }

    @Override
    public Response getUser() {
        String sub = (String) request.getAttribute("sub");
        try {
            UserInfo userInfo = userDatabaseManager.getUserInfoBySub(sub);
            if (userInfo == null) {
                userInfo = new UserInfo();
                userInfo.setSub(sub);
            }

            List<String> apiKeyNames = userDatabaseManager.getAPIKeyNamesBySub(sub);
            userInfo.setApiKeys(apiKeyNames.toArray(String[]::new));

            Object isAdminAttr = request.getAttribute(HttpConstants.OIDC.KEY_IS_ADMIN);
            if (isAdminAttr instanceof Boolean aBoolean) {
                userInfo.setIsAdmin(aBoolean);
            }

            String json = new ObjectMapper().writeValueAsString(userInfo);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).encoding("UTF-8").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response setUsername(String username) {
        HttpServletRequest req = new MutableServletRequest(request).withParameter("username", username);
        String sub = (String) req.getAttribute("sub");
        try {
            userDatabaseManager.updateUsername(sub, username);
            logger.info("User {} set his name to {}", sub, username);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response createApiKey(String name) {
        HttpServletRequest req = new MutableServletRequest(request).withParameter("name", name);
        String sub = (String) req.getAttribute("sub");
        String apiKey = APIKeyValidator.createAPIKey(sub);
        String hashedAPIKey = BCrypt.hashpw(apiKey, BCrypt.gensalt());
        String keyName = req.getParameter("name");

        try {
            userDatabaseManager.insertAPIKey(keyName, sub, hashedAPIKey);

            APIKeyInfo apiKeyInfo = new APIKeyInfo();
            apiKeyInfo.SetKey(apiKey);
            apiKeyInfo.setName(keyName);

            String json = new ObjectMapper().writeValueAsString(apiKeyInfo);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).encoding("UTF-8").build();
        } catch (Exception exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
    }

    @Override
    public Response revokeApiKey(String name) {
        HttpServletRequest req = new MutableServletRequest(request).withParameter("name", name);
        String sub = (String) req.getAttribute("sub");
        try {
            String keyName = req.getParameter("name");
            userDatabaseManager.deleteAPIKey(sub, keyName);
            return Response.ok().build();
        } catch (SQLException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
    }

    private Response doSaveEntry(HttpServletRequest req) {
        logger.info("Received save entry request.");
        long totalStart = System.currentTimeMillis();
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

            doShaclValidation(combinedModel, module);

            if (contentTypeLanguage != moduleLanguage) {
                StringWriter out = new StringWriter();
                RDFDataMgr.write(out, dataModel, moduleLanguage);
                rdfString = out.toString();
            }

            long headerStart = System.currentTimeMillis();
            headerDocument.writeModel(header.toModel(), Lang.JSONLD);
            long headerEnd = System.currentTimeMillis();

            long contentStart = System.currentTimeMillis();
            String contentDocumentPath = MossUtils.getContentStoragePath(resource, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
            contentDocument.writeDocument(rdfString, moduleLanguage);
            long contentEnd = System.currentTimeMillis();

            long totalEnd = System.currentTimeMillis();
            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("message", "Success");
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, module.getId(), moduleLanguage));

            long totalTime = totalEnd - totalStart;
            long headerTime = headerEnd - headerStart;
            long contentTime = contentEnd - contentStart;
            long remainingTime = totalTime - headerTime - contentTime;
            logger.info(
                    "Request profiling: total={}ms | header={}ms | content={}ms | remaining={}ms",
                    totalTime, headerTime, contentTime, remainingTime
            );

            return Response.ok(objectMapper.writeValueAsString(jsonResponse), HttpConstants.MediaTypes.APPLICATION_JSON).build();

        } catch (RDFParseException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Bad Request");
            error.put("message", e.getMessage());
            try {
                return Response.status(Response.Status.BAD_REQUEST)
                        .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                        .entity(objectMapper.writeValueAsString(error))
                        .build();
            } catch (IOException ex) {
                return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected exception caught", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", e.getMessage());
            try {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                        .entity(objectMapper.writeValueAsString(error))
                        .build();
            } catch (IOException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
            }
        }
    }

    private Response doDeleteEntry(HttpServletRequest req) {
        try {
            String requestBaseURL = ENV.MOSS_BASE_URL;
            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);

            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String moduleId = req.getParameter("module");
            var moduleOpt = store.loadModule(moduleId);
            if (moduleOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Module not found: " + moduleId)
                        .build();
            }

            var module = moduleOpt.get();
            Lang moduleLanguage = RDFLanguages.contentTypeToLang(module.getLanguage());

            String entryURI = MossUtils.getEntryUri(requestBaseURL, resource, module.getId());
            String headerDocumentPath = MossUtils.getHeaderStoragePath(resource, module.getId(), Lang.JSONLD);
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath, userInfo);

            logger.info("Resource: {}", resource);
            logger.info("Entry URI: {}", entryURI);
            logger.info("Storage Language: {}", moduleLanguage);

            int deletionResult = headerDocument.delete();
            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry header from database.");
            }

            String contentDocumentPath = MossUtils.getContentStoragePath(resource, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath, userInfo);
            deletionResult = contentDocument.delete();
            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry content from database.");
            }

            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("statusCode", "" + deletionResult);
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, module.getId(), moduleLanguage));
            String jsonResponseString = new ObjectMapper().writeValueAsString(jsonResponse);

            return Response.status(Response.Status.NO_CONTENT)
                    .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                    .entity(jsonResponseString)
                    .build();

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException caught", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (UnsupportedOperationException | ValidationException | RiotException | IOException e) {
            logger.error("Client error caught", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            logger.error("Unexpected exception caught", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    private Response doValidateEntry(HttpServletRequest req) {
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
                return Response.status(Response.Status.BAD_REQUEST).entity("Missing required parameter: module").build();
            }

            var moduleOpt = store.loadModule(moduleId);
            if (moduleOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND).entity("Module not found: " + moduleId).build();
            }

            MossModule module = moduleOpt.get();
            Dataset dataset = MossDatasetUtils.createEntryDataset(module, resourceUri, userInfo.getUsername(), contentModel);
            ValidationReport report = doShaclValidation(dataset.getUnionModel(), module);

            String acceptHeader = req.getHeader("Accept");
            Lang responseLang = RDFLanguages.contentTypeToLang(acceptHeader);
            if (responseLang == null) {
                responseLang = Lang.TURTLE;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            RDFDataMgr.write(out, report.getModel(), responseLang);

            return Response.status(report.conforms() ? Response.Status.OK : Response.Status.BAD_REQUEST)
                    .type(responseLang.getContentType().getContentTypeStr())
                    .entity(out.toString())
                    .build();

        } catch (Exception e) {
            logger.error("Validation error", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
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
