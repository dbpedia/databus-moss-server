package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MossProxyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private String baseUrl;

    final static Logger logger = LoggerFactory.getLogger(MossProxyServlet.class);

    public MossProxyServlet(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Build the target URL
        String targetUrl = buildTargetUrl(request);

        // Create a connection to the target URL
        HttpURLConnection connection;

        try {
            connection = (HttpURLConnection) new URI(targetUrl).toURL().openConnection();
            connection.setRequestMethod("GET");

            // Set the response status code and headers from the target response
            response.setStatus(connection.getResponseCode());
            connection.getHeaderFields().forEach((key, values) -> {
                if (key != null) {
                    for (String value : values) {
                        response.addHeader(key, value);
                    }
                }
            });

            // Stream the response content from the target URL to the client
            try (InputStream inputStream = connection.getInputStream();
                    OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException | URISyntaxException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }

    }

    private String buildTargetUrl(HttpServletRequest request) {
        StringBuilder targetUrl = new StringBuilder(baseUrl);

        // Append the request URI
        String requestUri = request.getRequestURI();
        targetUrl.append(requestUri);

        // Append the query string if it exists
        String queryString = request.getQueryString();
        if (queryString != null) {
            targetUrl.append('?').append(queryString);
        }

        return targetUrl.toString();
    }
}
