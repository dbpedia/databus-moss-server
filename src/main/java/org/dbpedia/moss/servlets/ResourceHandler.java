package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.servlets.modules.ModuleStore;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;
import org.dbpedia.moss.utils.RDFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of
 * related documents such as SHACL
 */
public class ResourceHandler {

    final static Logger logger = LoggerFactory.getLogger(ResourceHandler.class);

    private final ModuleStore moduleStore;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public ResourceHandler() {
        moduleStore = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String requestURI = req.getRequestURI();
        String requestPath = requestURI.substring(8);
        String extension = Lang.JSONLD.getFileExtensions().getFirst();

        String headerDocumentPath = String.format("/header/%s.%s", requestPath, extension);

        try {
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath);
            Model headerModel = headerDocument.readModel(Lang.JSONLD);

            if (headerModel == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            Resource resource = headerModel.getResource(ENV.MOSS_BASE_URL + requestURI);

            String contentGraphURI = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_CONTENT, null);
            String moduleUri = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_INSTANCE_OF, null);
            String moduleId = MossUtils.uriToName(moduleUri);

            var moduleRequest = moduleStore.loadModule(moduleId);

            if (moduleRequest.isEmpty()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Module not found: " + moduleId);
                return;
            }

            var module = moduleRequest.get();
            Lang contentLang = RDFLanguages.contentTypeToLang(module.getLanguage());

            List<HateoasLink> links = List.of(
                    new HateoasLink("self", requestURI),
                    new HateoasLink("alternate", requestURI, false, contentLang.getHeaderString()),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.TEXT_HTML),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                    new HateoasLink("alternate", requestURI, false, HttpConstants.MediaTypes.APPLICATION_JSON),
                    new HateoasLink("browse", MossUtils.navigateUp(requestURI), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON)
            );

            HttpUtils.addHateoasLinks(resp, links);

            List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(req);

            for (String acceptedType : acceptedTypes) {
                switch (acceptedType) {
                    case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                        ObjectNode hal = entryAsHAL(headerModel);
                        HttpUtils.addHateoasLinks(hal, links);
                        resp.setContentType(HttpConstants.MediaTypes.APPLICATION_HAL_JSON);
                        resp.getWriter().write(jsonMapper.writeValueAsString(hal));
                        return;
                    }
                    case HttpConstants.MediaTypes.TEXT_HTML -> {
                        ObjectNode hal = entryAsHAL(headerModel);
                        HttpUtils.addHateoasLinks(hal, links);
                        resp.setContentType(HttpConstants.MediaTypes.TEXT_HTML);
                        resp.getWriter().write(HttpUtils.getHtmlWrappedJson(resource.getLocalName(), hal));
                        return;
                    }
                    default -> {
                        // fallback to RDF
                        Lang contentTypeLanguage = MossUtils.getAcceptLang(req, Lang.JSONLD);

                        String contentDocumentPath = contentGraphURI.replace(String.format("%s/g/", ENV.MOSS_BASE_URL), "");
                        GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
                        Model contentModel = contentDocument.readModel(contentLang);

                        Model combinedModel = ModelFactory.createDefaultModel();
                        combinedModel.add(headerModel);
                        combinedModel.add(contentModel);

                        MossUtils.sendPrettyRDF(resp, contentTypeLanguage, combinedModel);
                        return;
                    }
                }
            }

        } catch (URISyntaxException e) {
            logger.error(e.getMessage());
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, e.getMessage());
        }

    }

    public ObjectNode entryAsHAL(Model headerModel) {
        ObjectNode hal = jsonMapper.createObjectNode();

        if (headerModel.isEmpty()) {
            return hal;
        }

        Resource resource = headerModel.listSubjectsWithProperty(RDF.type, RDFUris.MOSS_METADATA_ENTRY).nextResource();

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
