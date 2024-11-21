package org.dbpedia.moss.indexer;

public class MossLayer {

    private String name;
    private String formatMimeType;
    private String resourceType;
    private String templatePath;
    private String shaclPath;
    private String[] indexers;

    public String getFormatMimeType() {
        return formatMimeType;
    }

    public void setFormatMimeType(String format) {
        this.formatMimeType = format;
    }

    public String[] getIndexers() {
        return indexers;
    }

    public void setIndexers(String[] indexers) {
        this.indexers = indexers;
    }

    public String getShaclPath() {
        return shaclPath;
    }

    public void setShaclPath(String shaclPath) {
        this.shaclPath = shaclPath;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String scope) {
        this.resourceType = scope;
    }


    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String template) {
        this.templatePath = template;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
