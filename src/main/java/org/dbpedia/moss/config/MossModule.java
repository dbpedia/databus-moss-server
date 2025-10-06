package org.dbpedia.moss.config;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.RDFUris;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class MossModule {

    public static final String MODULE_FILE = "module.yml";
    public static final String CONTEXT_FILE = "context.jsonld";
    public static final String SHAPES_FILE = "shapes.ttl";
    public static final String INDEXER_FILE = "indexer.yml";

    private String id;
    private String label;
    private String description;
    private String language;

    public MossModule() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonIgnore
    public String getURI() {
        return ENV.MOSS_BASE_URL + "/module/" + getId();
    }

    @JsonIgnore
    public Model toModel() {
        Model model = ModelFactory.createDefaultModel();
        Resource moduleRes = model.createResource(getURI());

        moduleRes.addProperty(RDF.type, RDFUris.MOSS_METADATA_LAYER);

        if (label != null) {
            moduleRes.addProperty(RDFUris.DTC_TITLE, label);
        }

        if (description != null) {
            moduleRes.addProperty(RDFUris.DCT_DESCRIPTION, description);
        }

        if (language != null) {
            moduleRes.addProperty(RDFUris.MOSS_MIME_TYPE, language);
        }

        return model;
    }

}
