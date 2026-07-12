package org.dbpedia.moss.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class MutableServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> parameters = new HashMap<>();
    private final byte[] body;
    private final String contentType;

    public MutableServletRequest(HttpServletRequest request) {
        super(request);
        this.body = new byte[0];
        this.contentType = request.getContentType();
    }

    public MutableServletRequest(HttpServletRequest request, String body, String contentType) {
        super(request);
        this.body = body == null ? new byte[0] : body.getBytes();
        this.contentType = contentType;
    }

    public MutableServletRequest withParameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    @Override
    public String getParameter(String name) {
        if (parameters.containsKey(name)) {
            return parameters.get(name);
        }
        return super.getParameter(name);
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType : super.getContentType();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return input.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            encoding = "UTF-8";
        }
        try {
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }
}
