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
}
