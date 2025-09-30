package org.dbpedia.moss.config;


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
}