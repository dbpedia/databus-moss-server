package org.dbpedia.moss;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.dbpedia.moss.indexer.DataLoaderConfig;
import org.dbpedia.moss.indexer.LayerIndexerConfiguration;
import org.dbpedia.moss.indexer.MossLayer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

public class MossConfiguration {

    private List<MossLayer> layers;

    private List<DataLoaderConfig> loaders;

    private List<LayerIndexerConfiguration> indexers;

    // Getters and Setters for indexers, layers, loaders
    public List<LayerIndexerConfiguration> getIndexers() {
        return indexers;
    }

    public List<MossLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<MossLayer> layers) {
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
            for (MossLayer layer : config.getLayers()) {
                if (layer.getTemplatePath() != null && !layer.getTemplatePath().isEmpty()) {
                    // Resolve the template file relative to the config file's directory
                    File templateFile = new File(configDir, layer.getTemplatePath());
    
                    // Check if the template file exists and is readable
                    if (templateFile.exists() && templateFile.isFile()) {
                        // Read the content of the template file
                        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
                        layer.setTemplate(templateContent); // Set template content
                    } else {
                        System.err.println("Template file not found or not readable: " + templateFile.getAbsolutePath());
                    }
                }
            }
            
            HashMap<String, LayerIndexerConfiguration> indexerMap = new HashMap<>();

            // Load template content for each MossLayerType
            for (MossLayer layer : config.getLayers()) {
                for(String indexerPath : layer.getIndexers()) {

                    if(!indexerMap.containsKey(indexerPath)) {
                        LayerIndexerConfiguration newIndexerConfig = new LayerIndexerConfiguration();
                        newIndexerConfig.setConfigPath(indexerPath);
                        indexerMap.put(indexerPath, newIndexerConfig);
                    }

                    LayerIndexerConfiguration indexerConfig = indexerMap.get(indexerPath);
                    indexerConfig.addLayer(layer.getName());
                }
            }

            config.indexers = new ArrayList<>(indexerMap.values());


            return config;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
