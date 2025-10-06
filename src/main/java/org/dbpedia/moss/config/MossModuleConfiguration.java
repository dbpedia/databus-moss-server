package org.dbpedia.moss.config;

import java.util.List;

public class MossModuleConfiguration {
    private String id;
    private String label;
    private String description;
    private String schema;
    private String context;
    private String indexerConfig;
    private List<OntologyConfiguration> ontologies;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getIndexerConfig() {
        return indexerConfig;
    }

    public void setIndexerConfig(String indexerConfig) {
        this.indexerConfig = indexerConfig;
    }

    public List<OntologyConfiguration> getOntologies() {
        return ontologies;
    }

    public void setOntologies(List<OntologyConfiguration> ontologies) {
        this.ontologies = ontologies;
    }

    public static class OntologyConfiguration {
        private String name;
        private String path;
        private String indexer;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getIndexer() {
            return indexer;
        }

        public void setIndexer(String indexer) {
            this.indexer = indexer;
        }
    }
}
