package org.dbpedia.moss.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.RDFUris;

import com.fasterxml.jackson.annotation.JsonIgnore;


public class MossIndexerConfiguration {
    
    private String id;
    
    private String[] layers;

    @JsonIgnore
    private File configFile;

    @JsonIgnore
    private String configString;

    @JsonIgnore
    public File getConfigFile() {
        return configFile;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String[] getLayers() {
        return layers;
    }


    public void setLayers(String[] layers) {
        this.layers = layers;
    }

    public Model toModel(String baseURL) {
        Model model = ModelFactory.createDefaultModel();
        Resource indexerResource = model.createResource(getURI());
        Resource configResource = model.createResource(String.format("%s/config.yml", getURI()));
        model.add(indexerResource, RDF.type, RDFUris.MOSS_DATABUS_METADATA_LAYER_INDEXER);
        model.add(indexerResource, RDFUris.MOSS_CONFIG_FILE, configResource);
        return model;
    }

    @JsonIgnore
    public String getName() {
        return getName(id);
    }

    public static String getName(String id) {
        return id.split(":")[1];
    }

    public void createOrFetchConfigFile(MossConfiguration config) {
        String configPath = String.format("%s/%s.yml", config.getIndexerConfigPath(), getName());
        File configFile = new File(config.getConfigDir(), configPath);

        try {
            if (!configFile.exists()) {
                // Ensure parent directories exist
                configFile.getParentFile().mkdirs();

                // Create the file
                if (configFile.createNewFile()) {
                    // Optionally write default content
                    try (FileWriter writer = new FileWriter(configFile)) {
                        writer.write("# Default configuration\n");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config file: " + configFile.getAbsolutePath(), e);
        }

        this.configFile = configFile;
    }

    @JsonIgnore
    public String getURI() {
        return String.format("%s/indexer/%s", ENV.MOSS_BASE_URL, getName());
    }


    public boolean hasLayer(String layerId) {

        if(layers == null) {
            return false;
        }

        for(int i = 0; i < layers.length; i++) {
            if(layers[i].equals(layerId)) {
                return true;
            }
        }

        return false;
    } 

  
}