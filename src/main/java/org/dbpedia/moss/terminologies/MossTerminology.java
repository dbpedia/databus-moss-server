package org.dbpedia.moss.terminologies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.app.MossConfiguration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MossTerminology {

    public static final String DATA_FILE_NAME = "data";

    private String id;
    private String label;
    private String language;

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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonIgnore
    public String getURI() {
        return ENV.MOSS_BASE_URL + "/terminologies/" + getId();
    }

    @JsonIgnore
    public Path getDirectory() {
        return MossConfiguration.get().getTerminologyDirectory().toPath().resolve(getId());
    }

    @JsonIgnore
    public Path getDataFilePath() {

        return getDirectory().resolve(getDataFileName());
    }

    @JsonIgnore
    public String getDataFileName() {
        Lang lang = RDFLanguages.contentTypeToLang(language);
        String ext = "txt";
        if (lang != null && !lang.getFileExtensions().isEmpty()) {
            ext = lang.getFileExtensions().get(0);
        }
        return DATA_FILE_NAME + "." + ext;
    }

    @JsonIgnore
    public Model getDataModel() throws IOException {
        Path dataFile = getDataFilePath();
        if (!Files.exists(dataFile)) {
            throw new IOException("Data file not found: " + dataFile);
        }

        Model model = ModelFactory.createDefaultModel();
        Lang lang = RDFLanguages.contentTypeToLang(language);
        if (lang == null) {
            throw new IOException("Unknown RDF language: " + language);
        }

        RDFParser.source(dataFile)
                .lang(lang)
                .parse(model);
        return model;
    }

    @JsonIgnore
    public ObjectNode toJson() {

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode body = mapper.createObjectNode();
        body.put("id", getId());
        body.put("label", getLabel());
        body.put("language", getLanguage());

        return body;
    }
}
