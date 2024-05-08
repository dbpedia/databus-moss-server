package org.dbpedia.moss.indexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Loads data into the database and runs an indexer on startup
 */
public class DataLoaderConfig {
    private String collectionURI;
    private ModIndexerConfiguration indexer;

    public String getCollectionURI() {
        return collectionURI;
    }

    public void setCollectionURI(String collectionURI) {
        this.collectionURI = collectionURI;
    }

    public ModIndexerConfiguration getIndexer() {
        return indexer;
    }

    public void setIndexer(ModIndexerConfiguration indexer) {
        this.indexer = indexer;
    }

    public static ModIndexerConfiguration fromJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        return mapper.readValue(file, ModIndexerConfiguration.class);
    }

}