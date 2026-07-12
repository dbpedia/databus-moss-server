package org.dbpedia.moss.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class ServletPathRequest extends HttpServletRequestWrapper {

    private final String pathInfo;

    public ServletPathRequest(HttpServletRequest request, String pathInfo) {
        super(request);
        this.pathInfo = pathInfo;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    public static String toPathInfo(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
