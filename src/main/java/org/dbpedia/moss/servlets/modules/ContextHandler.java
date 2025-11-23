package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ContextHandler implements ISubResourceHandler {

    private static final String CONTEXT_FILE = "context.jsonld";

    private final ModuleStore store;

    public ContextHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<String> contentOpt = store.loadSubResource(moduleId, CONTEXT_FILE);
        if (contentOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Context not found for module: " + moduleId);
            return;
        }
        String content = contentOpt.get();

        // Build Link headers using HateoasLink
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + moduleId + "/context"),
                new HateoasLink("module", "/modules/" + moduleId),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.APPLICATION_LD_JSON),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("alternate", "/modules/" + moduleId + "/context", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedText(moduleId + " context", content);
                    resp.getWriter().write(html);
                    return;
                }
                case HttpConstants.MediaTypes.APPLICATION_LD_JSON, HttpConstants.MediaTypes.APPLICATION_JSON -> {
                    resp.setContentType(type);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(content);
                    return;
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(moduleId, CONTEXT_FILE, body);
        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_LD_JSON);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteSubResource(moduleId, CONTEXT_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Context not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
