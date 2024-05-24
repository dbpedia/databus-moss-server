package org.dbpedia.moss;

import java.util.List;

import org.dbpedia.moss.indexer.DataLoaderConfig;
import org.dbpedia.moss.indexer.LayerIndexerConfiguration;
import org.dbpedia.moss.indexer.MossLayer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;


public class MossConfiguration {
    private List<MossLayer> layers;
    private List<DataLoaderConfig> loaders;
    private List<LayerIndexerConfiguration> indexers;

    public List<LayerIndexerConfiguration> getIndexers() {
        return indexers;
    }

    public void setIndexers(List<LayerIndexerConfiguration> indexerConfigs) {
        this.indexers = indexerConfigs;
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
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        
            return mapper.readValue(file, MossConfiguration.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}