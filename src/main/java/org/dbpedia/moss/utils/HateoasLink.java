package org.dbpedia.moss.utils;

public final class HateoasLink {
    private final String rel;
    private final String href;
    private final boolean templated;
    private final String type;

    public HateoasLink(String rel, String href) {
        this(rel, href, false, null);
    }

    public HateoasLink(String rel, String href, boolean templated, String type) {
        this.rel = rel;
        this.href = href;
        this.templated = templated;
        this.type = type;
    }

    public String getRel() { return rel; }
    public String getHref() { return href; }
    public boolean isTemplated() { return templated; }
    public String getType() { return type; }
}
