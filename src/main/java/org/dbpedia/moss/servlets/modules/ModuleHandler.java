package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ModuleHandler {

    private final ModuleStore store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final IIndexerChangedHandler indexerChangedHandler;

    public ModuleHandler(IIndexerChangedHandler indexerChangedHandler) {
        this.indexerChangedHandler = indexerChangedHandler;
    }

    public void listModules(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<MossModule> modules = store.listModules();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules"),
                new HateoasLink("create", "/modules"),
                new HateoasLink("alternate", "/modules", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/modules", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode result = getHALJsonList(modules);
                    HttpUtils.addHateoasLinks(result, links);
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(result));
                    return;
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode result = getHALJsonList(modules);
                    HttpUtils.addHateoasLinks(result, links);
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedJson("modules", result);
                    resp.getWriter().write(html);
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
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

    public void getModule(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> contentOpt = store.loadModule(moduleId);
        if (contentOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }

        MossModule module = contentOpt.get();

        // HATEOAS links
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + module.getId()),
                new HateoasLink("alternate", "/modules/" + module.getId(), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/modules/" + module.getId(), false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("shapes", "/modules/" + module.getId() + "/shapes"),
                new HateoasLink("context", "/modules/" + module.getId() + "/context"),
                new HateoasLink("indexer", "/modules/" + module.getId() + "/indexer"),
                new HateoasLink("template", "/modules/" + module.getId() + "/template"),
                new HateoasLink("list", "/modules")
        );

        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode body = module.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(jsonMapper.writeValueAsString(body));
                    return;
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode body = module.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedJson(module.getId(), body);
                    resp.getWriter().write(html);
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    public void createModule(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        MossModule module = yamlMapper.readValue(req.getReader(), MossModule.class);

        if (module.getId() == null || module.getId().isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Module ID must be provided");
            return;
        }

        module.setId(module.getId().trim());

        try {
            store.saveModule(module);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save module: " + e.getMessage());
            return;
        }

        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_LD_JSON);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(yamlMapper.writeValueAsString(module));
    }

    public void updateModule(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> existing = store.loadModule(moduleId);
        if (existing.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }

        MossModule module = yamlMapper.readValue(req.getReader(), MossModule.class);
        module.setId(moduleId);

        try {
            store.saveModule(module);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (IOException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update module: " + e.getMessage());
            return;
        }

        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_LD_JSON);
        resp.getWriter().write(yamlMapper.writeValueAsString(module));
    }

    public void deleteModule(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteModule(moduleId);

        indexerChangedHandler.onIndexerChanged(moduleId);

        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
