package org.dbpedia.moss.indexer;

import java.util.ArrayList;
import java.util.List;


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
}