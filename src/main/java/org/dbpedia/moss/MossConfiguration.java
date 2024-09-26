package org.dbpedia.moss;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.dbpedia.moss.indexer.DataLoaderConfig;
import org.dbpedia.moss.indexer.LayerIndexerConfiguration;
import org.dbpedia.moss.indexer.MossLayerType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class MossConfiguration {
    private List<MossLayerType> layers;
    private List<DataLoaderConfig> loaders;
    private List<LayerIndexerConfiguration> indexers;

    // Getters and Setters for indexers, layers, loaders
    public List<LayerIndexerConfiguration> getIndexers() {
        return indexers;
    }

    public void setIndexers(List<LayerIndexerConfiguration> indexers) {
        this.indexers = indexers;
    }

    public List<MossLayerType> getLayers() {
        return layers;
    }

    public void setLayers(List<MossLayerType> layers) {
        this.layers = layers;
    }

    public List<DataLoaderConfig> getLoaders() {
        return loaders;
    }

    public void setLoaders(List<DataLoaderConfig> loaders) {
        this.loaders = loaders;
    }

    public static MossConfiguration fromJson(File file) {
        try {
            // Load the configuration from the YAML file
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
            MossConfiguration config = mapper.readValue(file, MossConfiguration.class);
    
            // Get the directory of the config file for resolving relative paths
            File configDir = file.getParentFile();
    
            // Load template content for each MossLayerType
            for (MossLayerType layer : config.getLayers()) {
                if (layer.getTemplate() != null && !layer.getTemplate().isEmpty()) {
                    // Resolve the template file relative to the config file's directory
                    File templateFile = new File(configDir, layer.getTemplate());
    
                    // Check if the template file exists and is readable
                    if (templateFile.exists() && templateFile.isFile()) {
                        // Read the content of the template file
                        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
                        layer.setTemplateContent(templateContent); // Set template content
                    } else {
                        System.err.println("Template file not found or not readable: " + templateFile.getAbsolutePath());
                    }
                }
            }
    
            return config;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
