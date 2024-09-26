package org.dbpedia.moss.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

public class SparqlProxyServlet extends HttpServlet {

    private final String sparqlEndpoint;

    public SparqlProxyServlet() {
		
		// MossEnvironment env = MossEnvironment.get();
        this.sparqlEndpoint = "http://localhost:5004/sparql"; // env.getGstoreBaseURL() + "/sparql";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        proxyRequest(req, resp, "GET");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        proxyRequest(req, resp, "POST");
    }

    private void proxyRequest(HttpServletRequest req, HttpServletResponse resp, String method) 
		throws IOException {
        // Construct the URL for the SPARQL endpoint with the query parameters
        String queryString = req.getQueryString();
        URL url;

		try {
			url = new URI(sparqlEndpoint + (queryString != null ? "?" + queryString : "")).toURL();
		} catch (MalformedURLException e) {
			resp.setStatus(400);
			e.printStackTrace();
			return;
		} catch (URISyntaxException e) {			
			resp.setStatus(400);
			e.printStackTrace();
			return;

		}

		System.out.println(url.toString());
        // Open a connection to the SPARQL endpoint
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        // Copy headers from the original request
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            connection.setRequestProperty(headerName, req.getHeader(headerName));
        }

        // For POST requests, send the body of the request
        if ("POST".equalsIgnoreCase(method)) {
            connection.setDoOutput(true);
            try (InputStream inputStream = req.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    connection.getOutputStream().write(buffer, bytesRead, 0);
                }
            }
        }

        // Get the response from the SPARQL endpoint and forward it to the client
        int responseCode = connection.getResponseCode();
        resp.setStatus(responseCode);

        // Copy response headers
        connection.getHeaderFields().forEach((key, values) -> {
            if (key != null) {
                values.forEach(value -> resp.addHeader(key, value));
            }
        });

        // Copy response body
        try (InputStream inputStream = connection.getInputStream()) {
	            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                resp.getOutputStream().write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            try (InputStream errorStream = connection.getErrorStream()) {
                if (errorStream != null) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = errorStream.read(buffer)) != -1) {
                        resp.getOutputStream().write(buffer, 0, bytesRead);
                    }
                }
            }
        }
    }
}
