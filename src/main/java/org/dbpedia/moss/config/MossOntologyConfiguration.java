package org.dbpedia.moss.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.File;
import java.io.IOException;


/**
 * Loads data into the database and runs an indexer on startup
 */
public class MossOntologyConfiguration {
    private String dataPath;
    private String indexerConfigPath;

    public String getIndexerConfigPath() {
        return indexerConfigPath;
    }

    public void setIndexerConfigs(String indexerConfigPath) {
        this.indexerConfigPath = indexerConfigPath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }


    public static MossIndexerConfiguration fromJson(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        return mapper.readValue(file, MossIndexerConfiguration.class);
    }

}