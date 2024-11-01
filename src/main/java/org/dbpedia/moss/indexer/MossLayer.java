package org.dbpedia.moss.indexer;

public class MossLayer {

    private String name;
    private String scope;
    private String templatePath;
    private String template;
    private String shaclPath;
    private String shacl;
    private String[] indexers;

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

    public String getShacl() {
        return shacl;
    }

    public void setShacl(String shacl) {
        this.shacl = shacl;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String templateContent) {
        this.template = templateContent;
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
