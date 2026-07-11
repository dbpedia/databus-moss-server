package org.dbpedia.moss.resources;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossTerminology;
import org.dbpedia.moss.generated.api.TerminologiesApi;
import org.dbpedia.moss.servlets.terminologies.TerminologyStore;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.HateoasLink;
import org.dbpedia.moss.utils.HttpConstants;
import org.dbpedia.moss.utils.HttpUtils;
import org.dbpedia.moss.utils.ResponseUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class TerminologiesResource implements TerminologiesApi {

    private static final String CONTENT_JSONLD = "application/ld+json";

    @Context
    private HttpServletRequest request;

    private final TerminologyStore store = new TerminologyStore(MossConfiguration.get().getTerminologyDirectory().toPath());
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @Override
    public Response listTerminologies() {
        List<MossTerminology> terminologies;
        try {
            terminologies = store.listTerminologies();
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to list terminologies: " + e.getMessage());
        }

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies"),
                new HateoasLink("create", "/terminologies"),
                new HateoasLink("alternate", "/terminologies", false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/terminologies", false, HttpConstants.MediaTypes.APPLICATION_JSON)
        );

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode result = getHalJsonList(terminologies);
                    return ResponseUtils.halOk(result, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode result = getHalJsonList(terminologies);
                    HttpUtils.addHateoasLinks(result, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson("terminologies", result),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render terminologies: " + e.getMessage());
                    }
                }
            }
        }
        return ResponseUtils.notAcceptable();
    }

    private ObjectNode getHalJsonList(List<MossTerminology> terminologies) {
        ObjectNode result = jsonMapper.createObjectNode();
        ArrayNode items = jsonMapper.createArrayNode();
        for (MossTerminology t : terminologies) {
            ObjectNode node = jsonMapper.createObjectNode();
            node.put("id", t.getId());
            node.put("label", t.getLabel());
            node.put("language", t.getLanguage());

            ObjectNode links = jsonMapper.createObjectNode();
            links.set("self", jsonMapper.createObjectNode().put("href", "/terminologies/" + t.getId()));
            node.set("_links", links);

            items.add(node);
        }
        ObjectNode embedded = jsonMapper.createObjectNode();
        embedded.set("terminologies", items);
        result.set("_embedded", embedded);
        return result;
    }

    @Override
    public Response getTerminology(String terminologyId) {
        Optional<MossTerminology> content;
        try {
            content = store.loadTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }
        if (content.isEmpty()) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }

        MossTerminology terminology = content.get();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies/" + terminology.getId()),
                new HateoasLink("alternate", "/terminologies/" + terminology.getId(), false, HttpConstants.MediaTypes.APPLICATION_HAL_JSON),
                new HateoasLink("alternate", "/terminologies/" + terminology.getId(), false, HttpConstants.MediaTypes.APPLICATION_JSON),
                new HateoasLink("data", "/terminologies/" + terminology.getId() + "/data", false, terminology.getLanguage()),
                new HateoasLink("list", "/terminologies"));

        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String acceptedType : acceptedTypes) {
            switch (acceptedType) {
                case HttpConstants.MediaTypes.APPLICATION_JSON, HttpConstants.MediaTypes.APPLICATION_HAL_JSON -> {
                    ObjectNode body = terminology.toJson();
                    return ResponseUtils.halOk(body, links);
                }
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    ObjectNode body = terminology.toJson();
                    HttpUtils.addHateoasLinks(body, links);
                    try {
                        Response.ResponseBuilder builder = Response.ok(
                                HttpUtils.getHtmlWrappedJson(terminology.getId(), body),
                                HttpConstants.MediaTypes.TEXT_HTML
                        );
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    } catch (IOException e) {
                        return ResponseUtils.serverError("Failed to render terminology: " + e.getMessage());
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response createTerminology(String body) {
        MossTerminology terminology;
        try {
            terminology = yamlMapper.readValue(body, MossTerminology.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid terminology payload: " + e.getMessage());
        }

        if (terminology.getId() == null || terminology.getId().isBlank()) {
            return ResponseUtils.badRequest("Terminology ID must be provided");
        }

        terminology.setId(terminology.getId().trim());

        try {
            if (store.loadTerminology(terminology.getId()).isPresent()) {
                return ResponseUtils.conflict("Terminology ID already exists");
            }
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }

        try {
            store.saveTerminology(terminology);
        } catch (IllegalArgumentException e) {
            return ResponseUtils.badRequest(e.getMessage());
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to save terminology: " + e.getMessage());
        }

        try {
            return ResponseUtils.created(yamlMapper.writeValueAsString(terminology), CONTENT_JSONLD);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to serialize terminology: " + e.getMessage());
        }
    }

    @Override
    public Response updateTerminology(String terminologyId, String body) {
        Optional<MossTerminology> existing;
        try {
            existing = store.loadTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }
        if (existing.isEmpty()) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }

        MossTerminology terminology;
        try {
            terminology = yamlMapper.readValue(body, MossTerminology.class);
        } catch (IOException e) {
            return ResponseUtils.badRequest("Invalid terminology payload: " + e.getMessage());
        }
        terminology.setId(terminologyId);

        try {
            store.saveTerminology(terminology);
        } catch (IllegalArgumentException e) {
            return ResponseUtils.badRequest(e.getMessage());
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to update terminology: " + e.getMessage());
        }

        try {
            return ResponseUtils.textOk(yamlMapper.writeValueAsString(terminology), CONTENT_JSONLD);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to serialize terminology: " + e.getMessage());
        }
    }

    @Override
    public Response deleteTerminology(String terminologyId) {
        boolean deleted;
        try {
            deleted = store.deleteTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete terminology: " + e.getMessage());
        }

        if (!deleted) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }
        return ResponseUtils.noContent();
    }

    @Override
    public Response getTerminologyData(String terminologyId) {
        Optional<MossTerminology> terminologyOpt;
        try {
            terminologyOpt = store.loadTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }
        if (terminologyOpt.isEmpty()) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }
        MossTerminology terminology = terminologyOpt.get();

        String filename = getDataFileName(terminology);
        Optional<String> contentOpt;
        try {
            contentOpt = store.loadSubResource(terminologyId, filename);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology data: " + e.getMessage());
        }
        if (contentOpt.isEmpty()) {
            return ResponseUtils.notFound("Data file not found for terminology: " + terminologyId);
        }
        String content = contentOpt.get();

        List<HateoasLink> links = List.of(
                new HateoasLink("self", "/terminologies/" + terminologyId + "/data"),
                new HateoasLink("terminology", "/terminologies/" + terminologyId),
                new HateoasLink("alternate", "/terminologies/" + terminologyId + "/data", false, getContentType(terminology)),
                new HateoasLink("alternate", "/terminologies/" + terminologyId + "/data", false, HttpConstants.MediaTypes.TEXT_HTML)
        );
        List<String> acceptedTypes = HttpUtils.getAcceptedMediaTypes(request);

        for (String type : acceptedTypes) {
            switch (type) {
                case HttpConstants.MediaTypes.TEXT_HTML -> {
                    Response.ResponseBuilder builder = Response.ok(
                            HttpUtils.getHtmlWrappedText(terminologyId, content),
                            HttpConstants.MediaTypes.TEXT_HTML
                    );
                    ResponseUtils.addLinkHeaders(builder, links);
                    return builder.build();
                }

                default -> {
                    if (type.equalsIgnoreCase(terminology.getLanguage())) {
                        Response.ResponseBuilder builder = Response.ok(content, terminology.getLanguage())
                                .encoding("UTF-8");
                        ResponseUtils.addLinkHeaders(builder, links);
                        return builder.build();
                    }
                }
            }
        }

        return ResponseUtils.notAcceptable();
    }

    @Override
    public Response updateTerminologyData(String terminologyId, String body) {
        Optional<MossTerminology> terminologyOpt;
        try {
            terminologyOpt = store.loadTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }
        if (terminologyOpt.isEmpty()) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }

        MossTerminology terminology = terminologyOpt.get();

        String expectedFilename = getDataFileName(terminology);
        String contentType = getContentType(terminology);
        Lang lang = RDFLanguages.contentTypeToLang(contentType);
        if (lang == null) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .entity("{\"message\":\"Unknown RDF language: " + contentType + "\"}")
                    .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                    .build();
        }

        try {
            RDFParser.create()
                    .source(new StringReader(body))
                    .lang(lang)
                    .parse(ModelFactory.createDefaultModel());

            store.saveSubResource(terminologyId, expectedFilename, body);

            onTerminologyChanged(terminologyId);

            return ResponseUtils.textOk(body, contentType);

        } catch (RiotException ex) {
            return ResponseUtils.badRequest("Invalid RDF data: " + ex.getMessage());
        } catch (IOException ex) {
            return ResponseUtils.serverError("Failed to store terminology data: " + ex.getMessage());
        }
    }

    @Override
    public Response deleteTerminologyData(String terminologyId) {
        Optional<MossTerminology> terminologyOpt;
        try {
            terminologyOpt = store.loadTerminology(terminologyId);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to load terminology: " + e.getMessage());
        }
        if (terminologyOpt.isEmpty()) {
            return ResponseUtils.notFound("Terminology not found: " + terminologyId);
        }
        MossTerminology terminology = terminologyOpt.get();

        String filename = getDataFileName(terminology);
        boolean deleted;
        try {
            deleted = store.deleteSubResource(terminologyId, filename);
        } catch (IOException e) {
            return ResponseUtils.serverError("Failed to delete terminology data: " + e.getMessage());
        }
        if (!deleted) {
            return ResponseUtils.notFound("Data file not found for terminology: " + terminologyId);
        }
        return ResponseUtils.noContent();
    }

    private String getDataFileName(MossTerminology terminology) {
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

    private void onTerminologyChanged(String terminologyId) {
        try {
            var terminologyResponse = store.loadTerminology(terminologyId);
            if (terminologyResponse.isEmpty()) {
                return;
            }

            var terminology = terminologyResponse.get();
            Lang terminologyLanguage = RDFLanguages.contentTypeToLang(terminology.getLanguage());
            String gstoreUri = terminology.getURI() + "." + terminologyLanguage.getFileExtensions().getFirst();
            GstoreResource gstoreTerminologyResource = new GstoreResource(gstoreUri);
            gstoreTerminologyResource.writeModel(
                    terminology.getDataModel(),
                    RDFLanguages.contentTypeToLang(terminology.getLanguage()));
        } catch (IOException | URISyntaxException e) {
            // logged by caller if needed
        }
    }
}
