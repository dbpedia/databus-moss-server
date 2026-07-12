package org.dbpedia.moss.auth;

import com.fasterxml.jackson.databind.JsonNode;

public class OIDCDiscoveryDocument {

    private static final String KEY_JWKS_URI = "jwks_uri";
    private static final String KEY_INTROSPECTION = "introspection_endpoint";
    private static final String KEY_USERINFO = "userinfo_endpoint";
    private static final String KEY_ISSUER = "issuer";

    private final JsonNode node;

    public OIDCDiscoveryDocument(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Discovery JSON node cannot be null");
        }
        this.node = node;
    }

    public String getJwksUri() {
        return getText(KEY_JWKS_URI);
    }

    public String getIntrospectionEndpoint() {
        return getText(KEY_INTROSPECTION);
    }

    public String getUserInfoEndpoint() {
        return getText(KEY_USERINFO);
    }

    public String getIssuer() {
        return getText(KEY_ISSUER);
    }

    private String getText(String key) {
        JsonNode value = node.get(key);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}
