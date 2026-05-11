package org.dbpedia.moss.servlets.facets;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class MossFacet {

    private String id;
    private String label;
    private String predicate;
    private int sortOrder;

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

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }


    public ObjectNode toJson(ObjectNode node) {
        node.put("id", id);
        node.put("label", label);
        node.put("predicate", predicate);
        node.put("sortOrder", sortOrder);
        return node;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
