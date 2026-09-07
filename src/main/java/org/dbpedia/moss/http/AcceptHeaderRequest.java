package org.dbpedia.moss.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class AcceptHeaderRequest extends HttpServletRequestWrapper {

    private final String accept;

    public AcceptHeaderRequest(HttpServletRequest request, String accept) {
        super(request);
        this.accept = accept;
    }

    @Override
    public String getHeader(String name) {
        if (HttpConstants.Headers.ACCEPT.equalsIgnoreCase(name)) {
            return accept;
        }
        return super.getHeader(name);
    }
}
