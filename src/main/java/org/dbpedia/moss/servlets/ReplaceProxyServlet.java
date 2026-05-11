package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

public class ReplaceProxyServlet extends ProxyServlet {

    private final String search;
    private final String replacement;

    public ReplaceProxyServlet(String baseUrl, String search, String replacement) {
        super(baseUrl);
        this.search = search;
        this.replacement = replacement;
    }

    @Override
    protected void onSendResponse(HttpURLConnection connection, HttpServletResponse response) throws IOException {
        try (InputStream in = connection.getInputStream();
             OutputStream out = response.getOutputStream()) {

            // Read entire response
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            // Apply replacement
            content = content.replace(search, replacement);

            var bytes = content.getBytes(StandardCharsets.UTF_8);

            response.setContentLength(bytes.length);
            out.write(bytes);
        }
    }
}
