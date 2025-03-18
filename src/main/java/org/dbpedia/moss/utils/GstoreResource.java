package org.dbpedia.moss.utils;


import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.dbpedia.moss.GstoreConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class GstoreResource {

    private final static Logger logger = LoggerFactory.getLogger("GSTORE");

    private static final String DOCUMENT_READ_ENDPOINT = "/document/read";
    private static final String DOCUMENT_WRITE_ENDPOINT = "/document/save";
    private static final String DOCUMENT_DELETE_ENDPOINT = "/document/delete";
    private static final String REQ_PARAM_REPO = "repo";
    private static final String REQ_PARAM_PATH = "path";
    private static final String REQ_PARAM_PREFIX = "prefix";

    private String repo;
    private String path;

    public GstoreResource(String uriString) throws URISyntaxException { 
        initialize(uriString);
    }

    private void initialize(String uriString) throws URISyntaxException {
        URI uri = new URI(uriString);

        // Extract the relative path (after the base URL)
        String relativePath = uri.getPath();
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("URI path is missing or empty.");
        }

        // Remove leading slash
        relativePath = MossUtils.pruneSlashes(relativePath); 


        if(relativePath.length() == 0) {
            throw new IllegalArgumentException("URI path is empty.");
        }

        // Split the path into parts by '/'
        String[] parts = relativePath.split("/");

        if (parts.length == 0) {
            throw new IllegalArgumentException("URI path does not contain any segments.");
        }

        // The first part is the repo
        this.repo = parts[0];

        if(repo == null || repo.length() == 0) {
            throw new IllegalArgumentException("Repo is null or empty.");
        }

        // The rest is the path
        if (parts.length > 1) {
            this.path = String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
        } else {
            this.path = ""; // If no additional path, we set it to an empty string
        }
    }

        // Constructor that takes a Resource (assumed to be a URI)
    public GstoreResource(Resource resource) throws URISyntaxException {
        if (resource == null || resource.getURI() == null) {
            throw new IllegalArgumentException("Resource or URI cannot be null.");
        }

        String uriString = resource.getURI();
        initialize(uriString);
    }

    public URL getRequestURL(GstoreOp operation) throws MalformedURLException, URISyntaxException {

        String apiRoute = getAPIRouteForOperation(operation);
        StringBuilder uri = new StringBuilder();
        uri.append(ENV.GSTORE_BASE_URL)
            .append(apiRoute)
            .append("?")
            .append(REQ_PARAM_REPO)
            .append("=")
            .append(URLEncoder.encode(repo, StandardCharsets.UTF_8))
            .append("&")
            .append(REQ_PARAM_PATH)
            .append("=")
            .append(URLEncoder.encode(path, StandardCharsets.UTF_8))
            .append("&")
            .append(REQ_PARAM_PREFIX)
            .append("=")
            .append(URLEncoder.encode(ENV.MOSS_BASE_URL + "/g/", StandardCharsets.UTF_8));
            
        return new URI(uri.toString()).toURL();
    }


        
    private String getAPIRouteForOperation(GstoreOp operation) {
        switch (operation) {
            case GstoreOp.Write:
                return DOCUMENT_WRITE_ENDPOINT;
            case GstoreOp.Read:
                return DOCUMENT_READ_ENDPOINT;
            case GstoreOp.Delete:
                return DOCUMENT_DELETE_ENDPOINT;
        }

        return null;
    }

    public String readDocument() throws URISyntaxException, IOException {
        String content = null;
            
        try {
            URL readURL = getRequestURL(GstoreOp.Read);
            logger.debug("Requesting gstore resource: {}", readURL.toString());

            HttpURLConnection connection = (HttpURLConnection) readURL.openConnection();
            connection.setRequestMethod(Constants.REQ_METHOD_GET);
            connection.setRequestProperty(Constants.HTTP_HEADER_ACCEPT, Lang.JSONLD.getHeaderString());
            connection.setRequestProperty(Constants.HTTP_HEADER_ACCEPT, Lang.JSONLD.getHeaderString());

            content = MossUtils.readToString(connection.getInputStream());

            connection.disconnect();
        } catch(FileNotFoundException e) {
            return null;
        }

        return content;
    }


    public boolean exists() {
        try {
            // Get the URL for the resource
            URL checkURL = getRequestURL(GstoreOp.Read);
            logger.debug("Checking existence of gstore resource: {}", checkURL.toString());
    
            // Open the connection and set the request method to HEAD
            HttpURLConnection connection = (HttpURLConnection) checkURL.openConnection();
            connection.setRequestMethod(Constants.REQ_METHOD_HEAD);
            connection.setRequestProperty(Constants.HTTP_HEADER_ACCEPT, Lang.JSONLD.getHeaderString());
    
            // Connect and get the response code
            int responseCode = connection.getResponseCode();
    
            // Disconnect the connection
            connection.disconnect();
    
            // Return true if the response code indicates success (2xx)
            return responseCode >= 200 && responseCode < 300;
        } catch (FileNotFoundException e) {
            // Resource not found
            return false;
        } catch (IOException | URISyntaxException e) {
            // Log unexpected exceptions and assume the resource doesn't exist
            logger.error("Error while checking resource existence", e);
            return false;
        }
    }

    public Model readModel(Lang lang) throws URISyntaxException, IOException {
        
        Model model = ModelFactory.createDefaultModel();
            
        try {
            URL readURL = getRequestURL(GstoreOp.Read);
            logger.info("Requesting gstore resource: {}", readURL.toString());

            HttpURLConnection connection = (HttpURLConnection) readURL.openConnection();
            connection.setRequestMethod(Constants.REQ_METHOD_GET);
            connection.setRequestProperty(Constants.HTTP_HEADER_ACCEPT, lang.getHeaderString());

            InputStream inputStream = connection.getInputStream();
            RDFParser.source(inputStream).forceLang(lang).parse(model);

            logger.info("Loaded model with " + model.size() + " triples.");
            inputStream.close();
            connection.disconnect();
        } catch(FileNotFoundException e) {
            return null;
        }

        return model;
    }
  
    public int delete() throws URISyntaxException, IOException {
        URL deleteURL = getRequestURL(GstoreOp.Delete);
        logger.debug("Build delete url <{}>.", deleteURL.toString());

        HttpURLConnection connection = (HttpURLConnection) deleteURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_DELETE);
        return connection.getResponseCode();
        
        //logger.info("Deleted resource  request returned {}.", responseCode);
    }

    public String getGraphURL() {
        return String.format("%s/g/%s/%s", ENV.MOSS_BASE_URL, repo, path);
    }

    // Getter for repo
    public String getRepo() {
        return repo;
    }

    // Getter for path
    public String getPath() {
        return path;
    }

    public void writeModel(Model model, Lang language) throws IOException, URISyntaxException {
        URL writeURL = getRequestURL(GstoreOp.Write);
        HttpURLConnection connection = (HttpURLConnection) writeURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_POST);
        connection.setRequestProperty(Constants.HTTP_HEADER_CONTENT_TYPE, language.getHeaderString());
        connection.setDoOutput(true);
    
        // Use try-with-resources to ensure outputStream is closed properly
        try (OutputStream outputStream = connection.getOutputStream()) {
            RDFDataMgr.write(outputStream, model, language);
            outputStream.flush();
        } catch (IOException e) {
            // Handle exception if writing fails
            throw new IOException("Failed to write model to output stream", e);
        } finally {
            // Ensure the connection is disconnected
            connection.disconnect();
        }
    
        // Get response code
        int responseCode = connection.getResponseCode();
        logger.debug("Response code: {}", responseCode);
    }

    public void writeDocument(String content, Lang lang) throws IOException, URISyntaxException {
        URL writeURL = getRequestURL(GstoreOp.Write);
        HttpURLConnection connection = (HttpURLConnection) writeURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_POST);
        connection.setRequestProperty(Constants.HTTP_HEADER_CONTENT_TYPE, lang.getHeaderString());
        connection.setDoOutput(true);

        // Use try-with-resources to ensure OutputStream is closed properly
        try (OutputStream outputStream = connection.getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write document to output stream", e);
        } finally {
            connection.disconnect(); // Ensure connection is closed
        }

        // Get response code
        int responseCode = connection.getResponseCode();
        logger.debug("Response code: {}", responseCode);

        if(responseCode != 200) {
            logger.error(connection.getResponseMessage());

            String errorMessage = GstoreConnector.readGstoreError(connection);
            throw new IOException("Failed to save the content document: " + errorMessage);
        }
    }
}
