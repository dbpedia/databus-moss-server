package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ModuleHandler {

    private static final String CONTENT_JSON = "application/json";
    private static final String CONTENT_JSONLD = "application/ld+json";

    private final ModuleStore store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final IModuleIndexerChangedHandler indexerChangedHandler;

    public ModuleHandler(IModuleIndexerChangedHandler indexerChangedHandler) {
        this.indexerChangedHandler = indexerChangedHandler;   
    }

    public void listModules(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<MossModule> modules = store.listModules();

        String result = jsonMapper.writeValueAsString(modules);
        resp.setContentType(CONTENT_JSON);
        resp.getWriter().write(result);
    }

    public void getModule(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> content = store.loadModule(moduleId);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }

        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write(yamlMapper.writeValueAsString(content.get()));
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

        resp.setContentType(CONTENT_JSONLD);
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

        resp.setContentType(CONTENT_JSONLD);
        resp.getWriter().write(yamlMapper.writeValueAsString(module));
    }

    public void deleteModule(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteModule(moduleId);

        indexerChangedHandler.onModuleIndexerChanged(moduleId);

        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
