package org.dbpedia.moss.servlets.terminologies;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.servlets.modules.ISubResourceHandler;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DataHandler implements ISubResourceHandler {

    private final TerminologyStore store;
    private final Consumer<String> changedHandler;

    public DataHandler(Consumer<String> changedHandler) {
        this.store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());
        this.changedHandler = changedHandler;
    }

    public String getDataFileName(MossTerminology terminology) {
        String language = terminology.getLanguage();
        Lang lang = RDFLanguages.contentTypeToLang(language);
        if (lang == null) {
            return "data.txt";
        }
        String ext = lang.getFileExtensions().isEmpty() ? "txt" : lang.getFileExtensions().get(0);
        return "data." + ext;
    }

    private String getContentType(MossTerminology terminology) {
        String language = terminology.getLanguage();
        return language != null ? language : "text/plain";
    }

    @Override
    public void get(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> terminologyOpt = store.loadTerminology(terminologyId);
        if (terminologyOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }
        MossTerminology terminology = terminologyOpt.get();

        String filename = getDataFileName(terminology);
        Optional<String> contentOpt = store.loadSubResource(terminologyId, filename);
        if (contentOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Data file not found for terminology: " + terminologyId);
            return;
        }
        String content = contentOpt.get();

        // Build Link headers using HateoasLink
        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies/" + terminologyId + "/data"),
                new HateoasLink("terminology", "/terminologies/" + terminologyId),
                new HateoasLink("alternate", "/terminologies/" + terminologyId + "/data", false, getContentType(terminology)),
                new HateoasLink("alternate", "/terminologies/" + terminologyId + "/data", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        HttpUtils.addHateoasLinks(resp, links);

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    String html = HttpUtils.getHtmlWrappedText(terminologyId, content);
                    resp.getWriter().write(html);
                    return;
                }

                default -> {
                    if (type.equalsIgnoreCase(terminology.getLanguage())) {
                        resp.setContentType(terminology.getLanguage());
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
    public void update(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> terminologyOpt = store.loadTerminology(terminologyId);
        if (terminologyOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }

        MossTerminology terminology = terminologyOpt.get();
        String body = req.getReader().lines().reduce("", (acc, line) -> acc + line + "\n");

        String expectedFilename = getDataFileName(terminology);
        String requestedPath = req.getPathInfo();
        String requestedFilename = requestedPath.substring(requestedPath.lastIndexOf('/') + 1);

        if (!expectedFilename.equals(requestedFilename)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Data file extension does not match terminology language. Expected: " + expectedFilename);
            return;
        }

        String contentType = getContentType(terminology);
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

            store.saveSubResource(terminologyId, expectedFilename, body);

            if (changedHandler != null) {
                changedHandler.accept(terminologyId);
            }

            resp.setContentType(contentType);
            resp.getWriter().write(body);

        } catch (RiotException ex) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid RDF data: " + ex.getMessage());
        }
    }

    @Override
    public void delete(HttpServletRequest req, HttpServletResponse resp, String terminologyId) throws IOException {
        Optional<MossTerminology> terminologyOpt = store.loadTerminology(terminologyId);
        if (terminologyOpt.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Terminology not found: " + terminologyId);
            return;
        }
        MossTerminology terminology = terminologyOpt.get();

        String filename = getDataFileName(terminology);
        boolean deleted = store.deleteSubResource(terminologyId, filename);
        if (!deleted) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Data file not found for terminology: " + terminologyId);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
