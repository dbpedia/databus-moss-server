package org.dbpedia.moss.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.RDFUris;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class MossLayerConfiguration {

    private String id;
    private String formatMimeType;
    private String[] resourceTypes;

    @JsonIgnore
    private File templateFile;
    
    @JsonIgnore
    private String shaclPath;

    public String getFormatMimeType() {
        return formatMimeType;
    }

    public void setFormatMimeType(String format) {
        this.formatMimeType = format;
    }

    public String getShaclPath() {
        return shaclPath;
    }

    public void setShaclPath(String shaclPath) {
        this.shaclPath = shaclPath;
    }

    public String[] getResourceTypes() {
        return resourceTypes;
    }

    public void setResourceTypes(String[] resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    public String getId() {
        return id;
    }

    public void setId(String name) {
        this.id = name;
    }

    public Model toModel() {

        Model model = ModelFactory.createDefaultModel();
        Resource layerResource = model.createResource(getURI());
        layerResource.addProperty(RDF.type, RDFUris.MOSS_METADATA_LAYER);
        layerResource.addProperty(RDFUris.MOSS_MIME_TYPE, formatMimeType);

        Lang layerLanguage = RDFLanguages.contentTypeToLang(formatMimeType);
        String extension = layerLanguage.getFileExtensions().getFirst();

        Resource templateResource = model.createResource(String.format("%s/template.%s", getURI(), extension));
        layerResource.addProperty( RDFUris.MOSS_TEMPLATE, templateResource);

        /*
        if(indexers != null) {
            
            MossConfiguration mossConfiguration = MossConfiguration.get();

            for(String indexerId : indexers) {
                MossIndexerConfiguration indexerConfiguration = mossConfiguration.getIndexer(indexerId);
                Resource indexResource = model.createResource(indexerConfiguration.getURI());
                model.add(layerResource, RDFUris.MOSS_INDEXER, indexResource);
            }
        }
             */
        // model.add(layerResource, RDFUris.MOSS_TEMPLATE, ResourceFactory.createResource(templatePath));

        /* 
        File shaclFile = new File(shaclPath);

        if (shaclFile.exists()) {
            Model shaclModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(shaclModel, shaclFile.getAbsolutePath(), Lang.TURTLE);
            model.add(shaclModel);

            Resource shaclResource = ResourceFactory.createResource(baseURL + "/api/shapes/" + shaclFile.getName());
            model.add(layerResource, RDFUris.MOSS_SHAPES_GRAPH, shaclResource);
        }
        */

        return model;
    }

    @JsonIgnore
    public String getName() {
        return id.split(":")[1];
    }

    @JsonIgnore
    public String getURI() {
        return String.format("%s/layer/%s", ENV.MOSS_BASE_URL, getName());
    }

    @JsonIgnore
    public File getTemplateFile() {
        return templateFile;
    }

    public void createOrFetchTemplateFile(MossConfiguration config) {

        
        Lang layerLanguage = RDFLanguages.contentTypeToLang(formatMimeType);
        String extension = layerLanguage.getFileExtensions().getFirst();

        String configPath = String.format("%s/%s.%s", config.getTemplatePath(), getName(), extension);
        File templateFile = new File(config.getConfigDir(), configPath);

        try {
            if (!templateFile.exists()) {
                // Ensure parent directories exist
                templateFile.getParentFile().mkdirs();

                // Create the file
                if (templateFile.createNewFile()) {
                    // Optionally write default content
                    try (FileWriter writer = new FileWriter(templateFile)) {
                        writer.write("");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create template file: " + templateFile.getAbsolutePath(), e);
        }

        this.templateFile = templateFile;
    } 
}
