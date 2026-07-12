package org.dbpedia.moss.storage;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.dbpedia.moss.app.Constants;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.app.MossUtils;
import org.dbpedia.moss.users.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GstoreResource {

    private final static Logger logger = LoggerFactory.getLogger("GSTORE");

    private static final String DOCUMENT_READ_ENDPOINT = "/document/read";
    private static final String DOCUMENT_WRITE_ENDPOINT = "/document/save";
    private static final String DOCUMENT_DELETE_ENDPOINT = "/document/delete";
    private static final String REQ_PARAM_REPO = "repo";
    private static final String REQ_PARAM_PATH = "path";
    private static final String REQ_PARAM_PREFIX = "prefix";
    private static final String REQ_PARAM_AUTHOR = "author";

    private String repo;
    private String path;
    private UserInfo userInfo;

    public GstoreResource(String uriString) throws URISyntaxException { 
        initialize(uriString);
    }

       public GstoreResource(String uriString, UserInfo userInfo) throws URISyntaxException { 
        initialize(uriString);
        this.userInfo = userInfo;
    }


    private void initialize(String uriString) throws URISyntaxException {
        URI uri = new URI(uriString);

        String relativePath = uri.getPath();
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("URI path is missing or empty.");
        }

        relativePath = MossUtils.pruneSlashes(relativePath); 


        if(relativePath.length() == 0) {
            throw new IllegalArgumentException("URI path is empty.");
        }

        String[] parts = relativePath.split("/");

        if (parts.length == 0) {
            throw new IllegalArgumentException("URI path does not contain any segments.");
        }

        this.repo = parts[0];

        if(repo == null || repo.length() == 0) {
            throw new IllegalArgumentException("Repo is null or empty.");
        }

        if (parts.length > 1) {
            this.path = String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
        } else {
            this.path = "";
        }
    }

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

        if(userInfo != null) {
            uri.append("&")
            .append(REQ_PARAM_AUTHOR)
            .append("=")
            .append(userInfo.getUsername());
        }
            
        return new URI(uri.toString()).toURL();
    }


        
    private String getAPIRouteForOperation(GstoreOp operation) {
        switch (operation) {
            case GstoreOp.Write -> {
                return DOCUMENT_WRITE_ENDPOINT;
            }
            case GstoreOp.Read -> {
                return DOCUMENT_READ_ENDPOINT;
            }
            case GstoreOp.Delete -> {
                return DOCUMENT_DELETE_ENDPOINT;
            }
        }

        return null;
    }

    public String readDocument() throws URISyntaxException, IOException {
        String content;
            
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
            URL checkURL = getRequestURL(GstoreOp.Read);
            logger.debug("Checking existence of gstore resource: {}", checkURL.toString());
    
            HttpURLConnection connection = (HttpURLConnection) checkURL.openConnection();
            connection.setRequestMethod(Constants.REQ_METHOD_HEAD);
            connection.setRequestProperty(Constants.HTTP_HEADER_ACCEPT, Lang.JSONLD.getHeaderString());
    
            int responseCode = connection.getResponseCode();
    
            connection.disconnect();
    
            return responseCode >= 200 && responseCode < 300;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException | URISyntaxException e) {
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

            try (InputStream inputStream = connection.getInputStream()) {
                RDFParser.source(inputStream).forceLang(lang).parse(model);
                logger.info("Loaded model with " + model.size() + " triples.");
            }
            connection.disconnect();
        } catch(FileNotFoundException e) {
            return null;
        }

        return model;
    }
  
    public int delete() throws URISyntaxException, IOException {
        URL deleteURL = getRequestURL(GstoreOp.Delete);
        logger.info("Build delete url <{}>.", deleteURL.toString());

        HttpURLConnection connection = (HttpURLConnection) deleteURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_DELETE);
        return connection.getResponseCode();
    }

    public String getGraphURL() {
        return String.format("%s/g/%s/%s", ENV.MOSS_BASE_URL, repo, path);
    }

    public String getRepo() {
        return repo;
    }

    public String getPath() {
        return path;
    }

    public void writeModel(Model model, Lang language) throws IOException, URISyntaxException {
        URL writeURL = getRequestURL(GstoreOp.Write);
        HttpURLConnection connection = (HttpURLConnection) writeURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_POST);
        connection.setRequestProperty(Constants.HTTP_HEADER_CONTENT_TYPE, language.getHeaderString());
        connection.setDoOutput(true);
    
        try (OutputStream outputStream = connection.getOutputStream()) {
            RDFDataMgr.write(outputStream, model, language);
            outputStream.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write model to output stream", e);
        } finally {
            connection.disconnect();
        }
    
        int responseCode = connection.getResponseCode();
        logger.debug("Response code: {}", responseCode);
    }

    public void writeDocument(String content, Lang lang) throws IOException, URISyntaxException {
        URL writeURL = getRequestURL(GstoreOp.Write);
        HttpURLConnection connection = (HttpURLConnection) writeURL.openConnection();
        connection.setRequestMethod(Constants.REQ_METHOD_POST);
        connection.setRequestProperty(Constants.HTTP_HEADER_CONTENT_TYPE, lang.getHeaderString());
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write document to output stream", e);
        } finally {
            connection.disconnect();
        }

        int responseCode = connection.getResponseCode();
        logger.debug("Response code: {}", responseCode);

        if(responseCode != 200) {
            logger.error(connection.getResponseMessage());

            String errorMessage = readGstoreError(connection);
            throw new IOException("Failed to save the content document: " + errorMessage);
        }
    }

    private static String readGstoreError(HttpURLConnection connection) throws IOException, UnsupportedEncodingException {
        InputStream inputStream = connection.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.toString());
        return jsonNode.path("message").asText();
    }
}
