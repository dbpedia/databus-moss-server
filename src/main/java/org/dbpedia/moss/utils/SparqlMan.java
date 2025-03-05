package org.dbpedia.moss.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SparqlMan {

    private static final Logger logger = LoggerFactory.getLogger(SparqlMan.class);
    
    public static String query(String query) throws IOException {
        try {
            // Create the SPARQL endpoint URL (assuming it's hosted on the same server at /sparql)
            URL sparqlEndpointURL = new URI(ENV.MOSS_BASE_URL + Constants.SPARQL_ENDPOINT_PATH).toURL(); 

            // Open connection to the SPARQL endpoint
            HttpURLConnection connection = (HttpURLConnection) sparqlEndpointURL.openConnection();
            connection.setRequestMethod(Constants.REQ_METHOD_POST);
            connection.setRequestProperty(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_FORM);
            connection.setDoOutput(true);
            
            String postData = Constants.SPARQL_FORM_POST_QUERY_PREFIX + URLEncoder.encode(query, StandardCharsets.UTF_8);
            connection.getOutputStream().write(postData.getBytes(StandardCharsets.UTF_8));

              // Read the response
            try (InputStream responseStream = connection.getInputStream()) {
                String response = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
                return response;
            }

        } catch (MalformedURLException | URISyntaxException e) {
            logger.error("Sparql endpoint not set up correctly: " + e.getMessage());
        }

        return null;
    }
}