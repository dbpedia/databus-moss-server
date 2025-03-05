package org.dbpedia.moss.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;


/**
 * Loads data into the database and runs an indexer on startup
 */
public class MossDataLoaderConfig {
    private String collectionURI;
    private String[] indexers;

    public String[] getIndexers() {
        return indexers;
    }

    public void setIndexers(String[] indexers) {
        this.indexers = indexers;
    }

    public String getCollectionURI() {
        return collectionURI;
    }

    public void setCollectionURI(String collectionURI) {
        this.collectionURI = collectionURI;
    }

  

    public static MossIndexerConfiguration fromJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        return mapper.readValue(file, MossIndexerConfiguration.class);
    }

}