package org.dbpedia.moss.indexer;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;


public class LayerIndexerConfiguration {
    private String configPath;
    private List<String> layers;

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public List<String> getLayers() {
        return layers;
    }

    public void addLayer(String layer) {
        if(layers == null) {
            layers = new ArrayList<String>();
        }

        layers.add(layer);
    }

    public void setLayers(List<String> layers) {
        this.layers = layers;
    }

    public static LayerIndexerConfiguration fromJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        return mapper.readValue(file, LayerIndexerConfiguration.class);
    }

}