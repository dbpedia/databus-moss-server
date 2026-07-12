package org.dbpedia.moss.modules;

import org.dbpedia.moss.app.ENV;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MossModule {

    public static final String MODULE_FILE = "module.yml";
    public static final String CONTEXT_FILE = "context.jsonld";
    public static final String SHAPES_FILE = "shapes.ttl";

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
        return ENV.MOSS_BASE_URL + "/modules/" + getId();
    }

    @JsonIgnore
    public ObjectNode toJson() {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode body = mapper.createObjectNode();
        body.put("id", getId());
        body.put("label", getLabel());
        body.put("description", getDescription());
        body.put("language", getLanguage());

        return body;
    }

}
