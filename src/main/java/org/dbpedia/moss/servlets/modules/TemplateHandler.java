package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TemplateHandler implements ISubResourceHandler {

    private final ModuleStore store;

    public TemplateHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
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
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> moduleOpt = store.loadModule(moduleId);
        if (moduleOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }
        MossModule module = moduleOpt.get();

        String filename = getTemplateFileName(module);
        Optional<String> content = store.loadSubResource(moduleId, filename);
        if (content.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Template not found for module: " + moduleId);
            return;
        }

        resp.setContentType(getContentType(module));
        resp.getWriter().write(content.get());
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> moduleOpt = store.loadModule(moduleId);
        if (moduleOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }
        MossModule module = moduleOpt.get();

        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        String expectedFilename = getTemplateFileName(module);

        String contentType = getContentType(module);
        Lang lang = RDFLanguages.contentTypeToLang(contentType);
        if (lang == null) {
            resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unknown RDF language: " + contentType);
            return;
        }

        try {
            RDFParser.create()
                    .source(new StringReader(body))
                    .lang(lang)
                    .parse(ModelFactory.createDefaultModel());
        } catch (RiotException ex) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid RDF template: " + ex.getMessage());
            return;
        }

        store.saveSubResource(moduleId, expectedFilename, body);
        resp.setContentType(contentType);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<MossModule> moduleOpt = store.loadModule(moduleId);
        if (moduleOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }
        MossModule module = moduleOpt.get();

        String filename = getTemplateFileName(module);
        boolean deleted = store.deleteSubResource(moduleId, filename);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Template not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
