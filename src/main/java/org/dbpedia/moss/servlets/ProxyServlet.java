package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final String baseUrl;

    private static final Logger logger = LoggerFactory.getLogger(ProxyServlet.class);

    public ProxyServlet(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String targetUrl = buildTargetUrl(request);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URI(targetUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            // Forward all request headers
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                Enumeration<String> values = request.getHeaders(header);
                while (values.hasMoreElements()) {
                    String value = values.nextElement();
                    connection.addRequestProperty(header, value);
                }
            }

            connection.connect();

            // Set the response status code
            response.setStatus(connection.getResponseCode());

            // Copy response headers
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null) {
                for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                    String key = entry.getKey();
                    if (key != null) {
                        for (String value : entry.getValue()) {
                            response.addHeader(key, value);
                        }
                    }
                }
            }

            onSendResponse(connection, response);

        } catch (IOException | URISyntaxException e) {
            logger.error("Proxy failed for URL {}: {}", targetUrl, e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected void onSendResponse(HttpURLConnection connection, HttpServletResponse response) throws IOException {
        // Stream content
        try (InputStream inputStream = connection.getInputStream();
                OutputStream outputStream = response.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    private String buildTargetUrl(HttpServletRequest request) {
        StringBuilder targetUrl = new StringBuilder(baseUrl);

        String pathInContext = request.getRequestURI().substring(request.getContextPath().length());
        if (pathInContext != null && !pathInContext.isEmpty()) {
            targetUrl.append(pathInContext);
        }

        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl.append('?').append(queryString);
        }

        return targetUrl.toString();
    }
}
