package org.dbpedia.moss.resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.generated.api.EntriesApi;
import org.dbpedia.moss.indexer.MossEntryHeader;
import org.dbpedia.moss.servlets.ValidationException;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;
import org.dbpedia.moss.utils.MossDatasetUtils;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.MutableServletRequest;
import org.dbpedia.moss.utils.RDFParseException;
import org.dbpedia.moss.utils.RDFUris;
import org.dbpedia.moss.utils.RDFUtils;
import org.dbpedia.moss.utils.ResponseUtils;
import org.dbpedia.moss.utils.ServletPathRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class EntriesResource implements EntriesApi {

    private static final Logger logger = LoggerFactory.getLogger(EntriesResource.class);

    @Context
    private HttpServletRequest request;

    private final UserDatabaseManager userDatabaseManager;
    private final String gstoreBaseUrl;
    private final ModuleStore moduleStore;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Inject
    public EntriesResource(UserDatabaseManager userDatabaseManager) {
        this.userDatabaseManager = userDatabaseManager;
        this.gstoreBaseUrl = ENV.GSTORE_BASE_URL;
        this.moduleStore = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public Response browseEntries() {
        return getEntry(new ServletPathRequest(request, ""));
    }

    @Override
    public Response getEntry(String path) {
        return getEntry(new ServletPathRequest(request, entriesPathInfo()));
    }

    @Override
    public Response deleteEntry(String path) {
        return deleteEntry(new ServletPathRequest(request, entriesPathInfo()));
    }

    private String entriesPathInfo() {
        String uri = request.getRequestURI();
        String prefix = "/entries";
        if (!uri.startsWith(prefix)) {
            return "";
        }
        String pathInfo = uri.substring(prefix.length());
        return pathInfo.isEmpty() ? "" : pathInfo;
    }

    @Override
    public Response createEntry(String module, String resource, String body) {
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.TEXT_TURTLE)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return doSaveEntry(req);
    }

    @Override
    public Response validateEntry(String module, String body, String resource) {
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.TEXT_TURTLE)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return doValidateEntry(req);
    }

    private Response getEntry(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            pathInfo = "";
        }

        try {
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath(pathInfo));
            Model headerModel = headerDocument.readModel(Lang.JSONLD);
            if (headerModel != null) {
                return getResource(req, headerModel);
            }
        } catch (IOException | URISyntaxException e) {
            logger.debug("No entry header at {}{}", "/entries", pathInfo, e);
        }

        return browse(req);
    }

    private static String headerDocumentPath(String pathInfo) {
        String path = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String extension = Lang.JSONLD.getFileExtensions().getFirst();
        return String.format("/header/%s.%s", path, extension);
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

            var moduleOpt = moduleStore.loadModule(moduleId);
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

            var moduleOpt = moduleStore.loadModule(moduleId);
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
        var loadShapesResult = moduleStore.loadSubResource(module.getId(), "shapes.ttl");
        if (loadShapesResult.isEmpty()) {
            logger.warn("No SHACL shapes found for module {}", module.getId());
            return ShaclValidator.get().validate(ModelFactory.createDefaultModel().getGraph(), dataModel.getGraph());
        }

        var shaclModel = MossDatasetUtils.parseShaclShapes(loadShapesResult.get());
        return ShaclValidator.get().validate(shaclModel.getGraph(), dataModel.getGraph());
    }

    private Response deleteEntry(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return Response.status(Response.Status.NOT_FOUND).entity("Entry path missing").build();
        }

        return deleteResource(req);
    }

    private Response browse(HttpServletRequest req) {
        String targetUrl = buildGstoreUrl(req);

        ObjectNode halNode;
        try {
            String halJson = fetchHalJson(targetUrl);
            if (halJson == null) {
                halNode = emptyBrowseHal();
            } else {
                halNode = (ObjectNode) jsonMapper.readTree(halJson);
            }
        } catch (IOException e) {
            return ResponseUtils.notFound("Failed to fetch from gstore: " + e.getMessage());
        }
        updateEmbeddedHAL(halNode);

        String requestURI = req.getRequestURI();
        List<HateoasLink> links = List.of(
                new HateoasLink("self", requestURI),
                new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.TEXT_HTML),
                new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("browse", navigateUp(requestURI), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON)
        );

        HttpUtils.addHateoasLinks(halNode, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        boolean htmlRequested = acceptedTypes.stream().anyMatch(t -> t.equals(HttpConstants.MediaTypes.TEXT_HTML));

        if (htmlRequested) {
            try {
                Response.ResponseBuilder builder = Response.ok(
                        HttpUtils.getHtmlWrappedJson("browse", halNode),
                        HttpConstants.MediaTypes.TEXT_HTML
                );
                ResponseUtils.addLinkHeaders(builder, links);
                return builder.build();
            } catch (IOException e) {
                return ResponseUtils.serverError("Failed to render HTML browse result: " + e.getMessage());
            }
        } else {
            return ResponseUtils.halOk(halNode, links);
        }
    }

    private String fetchHalJson(String targetUrl) throws IOException {
        URI uri = URI.create(targetUrl);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
        connection.setDoInput(true);

        connection.connect();

        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (status < 200 || status >= 300) {
                throw new IOException(targetUrl + " returned HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private ObjectNode emptyBrowseHal() {
        ObjectNode result = jsonMapper.createObjectNode();
        ObjectNode embedded = jsonMapper.createObjectNode();
        embedded.set("items", jsonMapper.createArrayNode());
        result.set("_embedded", embedded);
        return result;
    }

    private String buildGstoreUrl(HttpServletRequest req) {
        String path = req.getPathInfo();

        if (path == null) {
            path = "";
        }

        return gstoreBaseUrl + "/file/header" + path;
    }

    private String navigateUp(String requestURI) {
        int lastSlash = requestURI.lastIndexOf('/');
        return (lastSlash > 0) ? requestURI.substring(0, lastSlash) : "/";
    }

    private void updateEmbeddedHAL(ObjectNode halNode) {
        ArrayNode items = getEmbeddedItems(halNode);
        if (items == null) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            ObjectNode item = (ObjectNode) items.get(i);

            ObjectNode self = (ObjectNode) item.path("_links").path("self");
            if (self.has("href")) {
                String href = self.get("href").asText();

                if ("file".equals(item.path("type").asText())) {
                    item.put("type", "entry");
                    int lastDot = href.lastIndexOf('.');
                    if (lastDot > 0) {
                        href = href.substring(0, lastDot);
                    }

                    href = href.replace("/file/header", "/entries");
                    self.put("href", href);

                    item.put("name", href.substring(href.lastIndexOf('/') + 1));
                } else {
                    href = href.replace("/file/header", "/entries");
                    self.put("href", href);
                }
            }
        }
    }

    private ArrayNode getEmbeddedItems(ObjectNode halNode) {
        if (!halNode.has("_embedded")) {
            return null;
        }
        ObjectNode embedded = halNode.get("_embedded").isObject() ? (ObjectNode) halNode.get("_embedded") : null;
        if (embedded == null || !embedded.has("items")) {
            return null;
        }
        return embedded.get("items").isArray() ? (ArrayNode) embedded.get("items") : null;
    }

    private Response deleteResource(HttpServletRequest req) {
        try {
            UserInfo userInfo = MossUtils.getUserInfo(userDatabaseManager, req);

            String pathInfo = req.getPathInfo();
            String headerDocumentPath = headerDocumentPath(pathInfo);

            GstoreResource headerDocument = new GstoreResource(headerDocumentPath);
            Model headerModel = headerDocument.readModel(Lang.JSONLD);

            if (headerModel == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            Resource entryResource = headerModel.listSubjectsWithProperty(RDF.type, RDFUris.MOSS_METADATA_ENTRY).nextResource();
            var moduleURI = entryResource.getPropertyResourceValue(RDFUris.MOSS_INSTANCE_OF).getURI();
            var resourceUri = entryResource.getPropertyResourceValue(RDFUris.MOSS_EXTENDS).getURI();

            String moduleId = MossUtils.uriToName(moduleURI);

            var moduleRequest = moduleStore.loadModule(moduleId);

            if (moduleRequest.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"message\":\"Module not found: " + moduleId + "\"}")
                        .type("application/json")
                        .build();
            }

            var module = moduleRequest.get();

            Lang moduleLanguage = RDFLanguages.contentTypeToLang(module.getLanguage());

            int deletionResult = headerDocument.delete();

            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry header from database.");
            }

            String contentDocumentPath = MossUtils.getContentStoragePath(resourceUri, module.getId(), moduleLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath, userInfo);

            deletionResult = contentDocument.delete();

            if (deletionResult != 200) {
                throw new Exception("Unable to delete entry content from database.");
            }

            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("statusCode", "" + deletionResult);
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resourceUri, module.getId(), moduleLanguage));

            String jsonResponseString = jsonMapper.writeValueAsString(jsonResponse);

            return Response.status(Response.Status.NO_CONTENT)
                    .type("application/json")
                    .entity(jsonResponseString)
                    .build();

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException caught", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type("application/json")
                    .entity("{\"message\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (UnsupportedEncodingException | URISyntaxException | ValidationException | RiotException e) {
            logger.error("Client error caught", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .type("application/json")
                    .entity("{\"message\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            logger.error("Unexpected exception caught", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type("application/json")
                    .entity("{\"message\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    private Response getResource(HttpServletRequest req, Model headerModel) {
        String requestURI = req.getRequestURI();

        try {
            Resource resource = headerModel.getResource(ENV.MOSS_BASE_URL + requestURI);

            String contentGraphURI = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_CONTENT, null);
            String moduleUri = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_INSTANCE_OF, null);
            String moduleId = MossUtils.uriToName(moduleUri);

            var moduleRequest = moduleStore.loadModule(moduleId);

            if (moduleRequest.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .type("application/json")
                        .entity("{\"message\":\"Module not found: " + moduleId + "\"}")
                        .build();
            }

            var module = moduleRequest.get();
            Lang contentLang = RDFLanguages.contentTypeToLang(module.getLanguage());

            List<HateoasLink> links = List.of(
                    new HateoasLink("self", requestURI),
                    new HateoasLink("delete", requestURI),
                    new HateoasLink("alternate", requestURI, false, contentLang.getHeaderString()),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.TEXT_HTML),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_JSON),
                    new HateoasLink("browse", MossUtils.navigateUp(requestURI), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON)
            );

            List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

            for (String acceptedType : acceptedTypes) {
                switch (acceptedType) {
                    case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                        ObjectNode hal = entryAsHAL(headerModel);
                        HttpUtils.addHateoasLinks(hal, links);
                        Response.ResponseBuilder builder = Response.ok(
                                jsonMapper.writeValueAsString(hal),
                                HttpConstants.MediaTypes.APPLICATION_HAL_JSON
                        );
                        for (HateoasLink link : links) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("<").append(link.getHref()).append(">; rel=\"").append(link.getRel()).append("\"");
                            if (link.getType() != null) {
                                sb.append("; type=\"").append(link.getType()).append("\"");
                            }
                            if (link.isTemplated()) {
                                sb.append("; templated=true");
                            }
                            builder.header(HttpConstants.Headers.LINK, sb.toString());
                        }
                        return builder.build();
                    }
                    case HttpConstants.MediaTypes.TEXT_HTML -> {
                        ObjectNode hal = entryAsHAL(headerModel);
                        HttpUtils.addHateoasLinks(hal, links);
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson(resource.getLocalName(), hal),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        for (HateoasLink link : links) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("<").append(link.getHref()).append(">; rel=\"").append(link.getRel()).append("\"");
                            if (link.getType() != null) {
                                sb.append("; type=\"").append(link.getType()).append("\"");
                            }
                            if (link.isTemplated()) {
                                sb.append("; templated=true");
                            }
                            builder.header(HttpConstants.Headers.LINK, sb.toString());
                        }
                        return builder.build();
                    }
                    default -> {
                        Lang contentTypeLanguage = MossUtils.getAcceptLang(req, Lang.JSONLD);

                        String contentDocumentPath = contentGraphURI.replace(String.format("%s/g/", ENV.MOSS_BASE_URL), "");
                        GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
                        Model contentModel = contentDocument.readModel(contentLang);

                        Model combinedModel = ModelFactory.createDefaultModel();
                        combinedModel.add(headerModel);
                        combinedModel.add(contentModel);

                        return rdfResponse(contentTypeLanguage, combinedModel);
                    }
                }
            }

        } catch (URISyntaxException e) {
            logger.error(e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .type("application/json")
                    .entity("{\"message\":\"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type("application/json")
                    .entity("{\"message\":\"" + e.getMessage() + "\"}")
                    .build();
        }

        return Response.status(Response.Status.NOT_ACCEPTABLE).build();
    }

    private Response rdfResponse(Lang acceptLanguage, Model layerModel) throws IOException {
        if (acceptLanguage == Lang.JSONLD) {
            JsonObject compacted = RDFUtils.compact(layerModel);
            return Response.ok(compacted.toString(), acceptLanguage.getHeaderString()).build();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        layerModel.write(out, acceptLanguage.getName());
        return Response.ok(out.toString(), acceptLanguage.getHeaderString()).build();
    }

    private ObjectNode entryAsHAL(Model headerModel) {
        ObjectNode hal = jsonMapper.createObjectNode();

        if (headerModel.isEmpty()) {
            return hal;
        }

        Resource resource = headerModel.listSubjectsWithProperty(RDF.type, RDFUris.MOSS_METADATA_ENTRY).nextResource();

        hal.put("uri", resource.getURI());
        hal.put("module", resource.getPropertyResourceValue(RDFUris.MOSS_INSTANCE_OF).getURI());
        hal.put("extends", resource.getPropertyResourceValue(RDFUris.MOSS_EXTENDS).getURI());

        Statement createdStmt = resource.getProperty(DCTerms.created);
        if (createdStmt != null) {
            hal.put("created", createdStmt.getLiteral().getString());
        }

        Statement modifiedStmt = resource.getProperty(DCTerms.modified);
        if (modifiedStmt != null) {
            hal.put("modified", modifiedStmt.getLiteral().getString());
        }

        hal.put("contentGraph", resource.getPropertyResourceValue(RDFUris.MOSS_CONTENT).getURI());

        return hal;
    }
}
