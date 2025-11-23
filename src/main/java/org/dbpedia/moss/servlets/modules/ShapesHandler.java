package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ShapesHandler implements ISubResourceHandler {

    private static final String SHAPES_FILE = "shapes.ttl";

    private final ModuleStore store;

    public ShapesHandler() {
        store = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        Optional<String> contentOpt = store.loadSubResource(moduleId, MossModule.SHAPES_FILE);
        if (contentOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Shapes not found for module: " + moduleId);
            return;
        }
        String content = contentOpt.get();

        // Build Link headers using HateoasLink
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/modules/" + moduleId + "/shapes"),
                new HateoasLink("module", "/modules/" + moduleId),
                new HateoasLink("alternate", "/modules/" + moduleId + "/shapes", false, "text/turtle"),
                new HateoasLink("alternate", "/modules/" + moduleId + "/shapes", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedText(moduleId + " shapes", content);
                    resp.getWriter().write(html);
                    return;
                }

                default -> {
                    if (type.equalsIgnoreCase(HttpConstants.MediaTypes.TEXT_TURTLE)) {
                        resp.setContentType("text/turtle");
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().write(content);
                        return;
                    }
                }
            }
        }

        resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Requested content type not supported");
    }

    @Override
    public void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");
        store.saveSubResource(moduleId, SHAPES_FILE, body);
        resp.setContentType(HttpConstants.MediaTypes.TEXT_TURTLE);
        resp.getWriter().write(body);
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException {
        boolean deleted = store.deleteSubResource(moduleId, SHAPES_FILE);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Shapes not found for module: " + moduleId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
