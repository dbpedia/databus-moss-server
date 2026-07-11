package org.dbpedia.moss.utils;

public final class HttpConstants {

    private HttpConstants() {}

    public static final class Headers {
        private Headers() {}
        public static final String ACCEPT = "Accept";
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String ETAG = "ETag";
        public static final String LOCATION = "Location";
        public static final String LINK = "Link";
        public static final String AUTHORIZATION = "Authorization";
    }

    public static final class MediaTypes {
        private MediaTypes() {}
        public static final String APPLICATION_JSON = "application/json";
        public static final String APPLICATION_HAL_JSON = "application/hal+json";
        public static final String APPLICATION_LD_JSON = "application/ld+json";
        public static final String TEXT_TURTLE = "text/turtle";
        public static final String TEXT_HTML = "text/html";
        public static final String APPLICATION_SPARQL_QUERY = "application/sparql-query";
    }

    public static final class Methods {
        private Methods() {}
        public static final String DELETE = "DELETE";
        public static final String GET = "GET";
        public static final String HEAD = "HEAD";
        public static final String OPTIONS = "OPTIONS";
        public static final String PATCH = "PATCH";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
    }

    public static final class OIDC {
        private OIDC() {}
        public static final String DISCOVERY_DOCUMENT = "/.well-known/openid-configuration";
        public static final String KEY_SUBJECT = "sub";
        public static final String KEY_ROLES = "roles";
        public static final String KEY_IS_ADMIN = "isAdmin";
        public static final String KEY_NAME = "name";
        public static final String KEY_GIVEN_NAME = "given_name";
        public static final String KEY_FAMILY_NAME = "family_name";
        public static final String KEY_PREFERRED_USERNAME = "preferred_username";
        public static final String DISCOVERY_KEY_ISSUER = "issuer";
        public static final String DISCOVERY_KEY_JWKS_URI = "jwks_uri";
        public static final String DISCOVERY_KEY_INTROSPECTION_ENDPOINT = "introspection_endpoint";
        public static final String DISCOVERY_KEY_USERINFO_ENDPOINT = "userinfo_endpoint";
        public static final String KEY_REALM_ACCESS = "realm_access";
        public static final String KEY_RESOURCE_ACCESS = "resource_access";
    }
}
