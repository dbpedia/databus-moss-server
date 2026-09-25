package org.dbpedia.moss.modules;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.app.MossConfiguration;
import org.dbpedia.moss.generated.api.ModulesApi;
import org.dbpedia.moss.http.AcceptHeaderRequest;
import org.dbpedia.moss.http.HateoasLink;
import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.http.HttpUtils;
import org.dbpedia.moss.http.ResponseUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class ModulesResource implements ModulesApi {

    private static final String CONTEXT_FILE = "context.jsonld";
    private static final String SHAPES_FILE = "shapes.ttl";

    @Context
    private HttpServletRequest request;

    private final ModuleStore store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public Response listModules() {
        List<MossModule> modules;
        try {
            modules = store.listModules();
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to list modules: " + e.getMessage());
        }

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules"),
                new HateoasLink("create", "/modules"),
                new HateoasLink("alternate", "/modules", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/modules", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode result = getHALJsonList(modules);
                    return ResponseUtils.halOk(result, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode result = getHALJsonList(modules);
                    HttpUtils.addHateoasLinks(result, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson("modules", result),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render modules: " + e.getMessage());
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    private ObjectNode getHALJsonList(List<MossModule> modules) {
        ObjectNode result = jsonMapper.createObjectNode();
        ArrayNode items = jsonMapper.createArrayNode();

        for (MossModule m : modules) {
            ObjectNode node = jsonMapper.createObjectNode();
            node.put("id", m.getId());
            node.put("label", m.getLabel());
            node.put("description", m.getDescription());
            node.put("language", m.getLanguage());

            ObjectNode links = jsonMapper.createObjectNode();
            links.set("self", jsonMapper.createObjectNode().put("href", "/modules/" + m.getId()));
            node.set("_links", links);

            items.add(node);
        }

        ObjectNode embedded = jsonMapper.createObjectNode();
        embedded.set("modules", items);
        result.set("_embedded", embedded);

        return result;
    }

    @Override
    public Response getModule(String moduleId) {
        Optional<MossModule> contentOpt;
        try {
            contentOpt = store.loadModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load module: " + e.getMessage());
        }
        if (contentOpt.isEmpty()) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }

        MossModule module = contentOpt.get();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + module.getId()),
                new HateoasLink("alternate", "/modules/" + module.getId(), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/modules/" + module.getId(), false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("shapes", "/modules/" + module.getId() + "/shapes"),
                new HateoasLink("context", "/modules/" + module.getId() + "/context"),
                new HateoasLink("template", "/modules/" + module.getId() + "/template"),
                new HateoasLink("list", "/modules")
        );

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode body = module.toJson();
                    return ResponseUtils.halOk(body, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode body = module.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson(module.getId(), body),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render module: " + e.getMessage());
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response createModule(String body) {
        MossModule module;
        try {
            module = yamlMapper.readValue(body, MossModule.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid module payload: " + e.getMessage());
        }

        if (module.getId() == null || module.getId().isBlank()) {
            return ResponseUtils.badRequest("Module ID must be provided");
        }

        module.setId(module.getId().trim());

        try {
            store.saveModule(module);
        } catch (IllegalArgumentException e) {
            return ResponseUtils.badRequest(e.getMessage());
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save module: " + e.getMessage());
        }

        try {
            return ResponseUtils.created(
                    yamlMapper.writeValueAsString(module),
                    HttpConstants.MediaTypes.APPLICATION_LD_JSON
            );
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to serialize module: " + e.getMessage());
        }
    }

    @Override
    public Response updateModule(String moduleId, String body) {
        Optional<MossModule> existing;
        try {
            existing = store.loadModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load module: " + e.getMessage());
        }
        if (existing.isEmpty()) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }

        MossModule module;
        try {
            module = yamlMapper.readValue(body, MossModule.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid module payload: " + e.getMessage());
        }
        module.setId(moduleId);

        try {
            store.saveModule(module);
        } catch (IllegalArgumentException e) {
            return ResponseUtils.badRequest(e.getMessage());
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to update module: " + e.getMessage());
        }

        try {
            return ResponseUtils.textOk(
                    yamlMapper.writeValueAsString(module),
                    HttpConstants.MediaTypes.APPLICATION_LD_JSON
            );
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to serialize module: " + e.getMessage());
        }
    }

    @Override
    public Response deleteModule(String moduleId) {
        boolean deleted;
        try {
            deleted = store.deleteModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete module: " + e.getMessage());
        }

        if (!deleted) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }
        return ResponseUtils.noContent();
    }

    @Override
    public Response getModuleContext(String moduleId) {
        return getModuleContext(moduleId, request);
    }

    @Override
    public Response getModuleContextJsonLd(String moduleId) {
        return getModuleContext(
                moduleId,
                new AcceptHeaderRequest(request, HttpConstants.MediaTypes.APPLICATION_LD_JSON)
        );
    }

    private Response getModuleContext(String moduleId, HttpServletRequest req) {
        Optional<String> contentOpt;
        try {
            contentOpt = store.loadSubResource(moduleId, CONTEXT_FILE);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load context: " + e.getMessage());
        }
        if (contentOpt.isEmpty()) {
            return ResponseUtils.notFound("Context not found for module: " + moduleId);
        }
        String content = contentOpt.get();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + moduleId + "/context"),
                new HateoasLink("module", "/modules/" + moduleId),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context.jsonld", false, HttpConstants.MediaTypes.APPLICATION_LD_JSON),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.APPLICATION_LD_JSON),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    Response.ResponseBuilder builder = Response.ok(
                            HttpUtils.getHtmlWrappedText(moduleId + " context", content),
                            HttpConstants.MediaTypes.TEXT_HTML
                    );
                    ResponseUtils.addLinkHeaders(builder, links);
                    return builder.build();
                }
                case HttpConstants.MediaTypes.APPLICATION_LD_JSON, HttpConstants.MediaTypes.APPLICATION_JSON -> {
                    Response.ResponseBuilder builder = Response.ok(content, type);
                    ResponseUtils.addLinkHeaders(builder, links);
                    return builder.build();
                }
                default -> {
                    Response.ResponseBuilder builder = Response.ok(content, HttpConstants.MediaTypes.APPLICATION_LD_JSON);
                    ResponseUtils.addLinkHeaders(builder, links);
                    return builder.build();
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response updateModuleContext(String moduleId, String body) {
        try {
            store.saveSubResource(moduleId, CONTEXT_FILE, body);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save context: " + e.getMessage());
        }
        return ResponseUtils.textOk(body, HttpConstants.MediaTypes.APPLICATION_LD_JSON);
    }

    @Override
    public Response deleteModuleContext(String moduleId) {
        boolean deleted;
        try {
            deleted = store.deleteSubResource(moduleId, CONTEXT_FILE);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete context: " + e.getMessage());
        }
        if (!deleted) {
            return ResponseUtils.notFound("Context not found for module: " + moduleId);
        }
        return ResponseUtils.noContent();
    }

    @Override
    public Response getModuleShapes(String moduleId) {
        Optional<String> contentOpt;
        try {
            contentOpt = store.loadSubResource(moduleId, MossModule.SHAPES_FILE);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load shapes: " + e.getMessage());
        }
        if (contentOpt.isEmpty()) {
            return ResponseUtils.notFound("Shapes not found for module: " + moduleId);
        }
        String content = contentOpt.get();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + moduleId + "/shapes"),
                new HateoasLink("module", "/modules/" + moduleId),
                new HateoasLink("alternate", "/modules/" + moduleId + "/shapes", false, "text/turtle"),
                new HateoasLink("alternate", "/modules/" + moduleId + "/shapes", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    Response.ResponseBuilder builder = Response.ok(
                            HttpUtils.getHtmlWrappedText(moduleId + " shapes", content),
                            HttpConstants.MediaTypes.TEXT_HTML
                    );
                    ResponseUtils.addLinkHeaders(builder, links);
                    return builder.build();
                }

                default -> {
                    if (type.equalsIgnoreCase(HttpConstants.MediaTypes.TEXT_TURTLE) || "*/*".equals(type)) {
                        Response.ResponseBuilder builder = Response.ok(content, HttpConstants.MediaTypes.TEXT_TURTLE);
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response updateModuleShapes(String moduleId, String body) {
        try {
            store.saveSubResource(moduleId, SHAPES_FILE, body);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save shapes: " + e.getMessage());
        }
        return ResponseUtils.textOk(body, HttpConstants.MediaTypes.TEXT_TURTLE);
    }

    @Override
    public Response deleteModuleShapes(String moduleId) {
        boolean deleted;
        try {
            deleted = store.deleteSubResource(moduleId, SHAPES_FILE);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete shapes: " + e.getMessage());
        }
        if (!deleted) {
            return ResponseUtils.notFound("Shapes not found for module: " + moduleId);
        }
        return ResponseUtils.noContent();
    }

    private String getTemplateFileName(MossModule module) {
        String language = module.getLanguage();
        Lang lang = RDFLanguages.contentTypeToLang(language);
        if (lang == null) {
            return "template.txt";
        }
        String ext = lang.getFileExtensions().isEmpty() ? "txt" : lang.getFileExtensions().get(0);
        return "template." + ext;
    }

    private String getContentType(MossModule module) {
        String language = module.getLanguage();
        return language != null ? language : "text/plain";
    }

    @Override
    public Response getModuleTemplate(String moduleId) {
        Optional<MossModule> moduleOpt;
        try {
            moduleOpt = store.loadModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load module: " + e.getMessage());
        }
        if (moduleOpt.isEmpty()) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }
        MossModule module = moduleOpt.get();

        String filename = getTemplateFileName(module);
        Optional<String> content;
        try {
            content = store.loadSubResource(moduleId, filename);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load template: " + e.getMessage());
        }
        if (content.isEmpty()) {
            return ResponseUtils.notFound("Template not found for module: " + moduleId);
        }

        return ResponseUtils.textOk(content.get(), getContentType(module));
    }

    @Override
    public Response updateModuleTemplate(String moduleId, String body) {
        Optional<MossModule> moduleOpt;
        try {
            moduleOpt = store.loadModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load module: " + e.getMessage());
        }
        if (moduleOpt.isEmpty()) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }
        MossModule module = moduleOpt.get();

        String expectedFilename = getTemplateFileName(module);

        String contentType = getContentType(module);
        Lang lang = RDFLanguages.contentTypeToLang(contentType);
        if (lang == null) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .entity("{\"message\":\"Unknown RDF language: " + contentType + "\"}")
                    .type("application/json")
                    .build();
        }

        try {
            RDFParser.create()
                    .source(new StringReader(body))
                    .lang(lang)
                    .parse(ModelFactory.createDefaultModel());
        } catch (RiotException ex) {
            return ResponseUtils.badRequest("Invalid RDF template: " + ex.getMessage());
        }

        try {
            store.saveSubResource(moduleId, expectedFilename, body);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save template: " + e.getMessage());
        }

        return ResponseUtils.textOk(body, contentType);
    }

    @Override
    public Response deleteModuleTemplate(String moduleId) {
        Optional<MossModule> moduleOpt;
        try {
            moduleOpt = store.loadModule(moduleId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load module: " + e.getMessage());
        }
        if (moduleOpt.isEmpty()) {
            return ResponseUtils.notFound("Module not found: " + moduleId);
        }
        MossModule module = moduleOpt.get();

        String filename = getTemplateFileName(module);
        boolean deleted;
        try {
            deleted = store.deleteSubResource(moduleId, filename);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete template: " + e.getMessage());
        }
        if (!deleted) {
            return ResponseUtils.notFound("Template not found for module: " + moduleId);
        }
        return ResponseUtils.noContent();
    }
}
