package org.dbpedia.moss.config;

import java.util.List;

public class LookupIndexer {

    private String version;
    private String indexMode;
    private String sparqlEndpoint;
    private List<IndexField> indexFields;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIndexMode() {
        return indexMode;
    }

    public void setIndexMode(String indexMode) {
        this.indexMode = indexMode;
    }

    public String getSparqlEndpoint() {
        return sparqlEndpoint;
    }

    public void setSparqlEndpoint(String sparqlEndpoint) {
        this.sparqlEndpoint = sparqlEndpoint;
    }

    public List<IndexField> getIndexFields() {
        return indexFields;
    }

    public void setIndexFields(List<IndexField> indexFields) {
        this.indexFields = indexFields;
    }

    public static class IndexField {

        private String fieldName;
        private String documentVariable;
        private String type;
        private String query;

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getDocumentVariable() {
            return documentVariable;
        }

        public void setDocumentVariable(String documentVariable) {
            this.documentVariable = documentVariable;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
