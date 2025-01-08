package org.dbpedia.moss;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shared.PropertyNotFoundException;
import org.dbpedia.moss.indexer.MossLayerHeader;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;
import org.dbpedia.moss.utils.RDFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class GstoreConnector {

    final static Logger logger = LoggerFactory.getLogger(GstoreConnector.class);

    private static final String REQ_METHOD_POST = "POST";
    private static final String REQ_METHOD_GET = "GET";
    private static final String CHAR_ENCODING_UTF8 ="UTF-8";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_LD_JSON = "application/ld+json";
    private static final String DOCUMENT_READ_ENDPOINT = "/document/read";
    private static final String DOCUMENT_SAVE_ENDPOINT = "/document/save";
    private static final String DOCUMENT_HISTORY_ENDPOINT = "/document/history";
    private static final String REQ_PARAM_REPO = "repo";
    private static final String REQ_PARAM_PATH = "path";
    private static final String REQ_PARAM_PREFIX = "prefix";
    // private static final String REQ_AUTHOR_NAME = "author_name";
    private static final String LAYER_FRAGMENT = "#layer";

    private String gstoreBaseURL;

    public GstoreConnector(String baseURL) {
        this.gstoreBaseURL = baseURL;
    }

    /**
     * Retrieves the layer header based on Databus resource URI and layer name
     * @param layerName
     * @param resource
     * @return
     * @throws URISyntaxException
     * @throws IOException
    
    public MossLayerHeader getLayerHeader(String layerName, String resource)
        throws URISyntaxException, IOException {

        // Create the layer identifier from resource and layerName
        String layerURI = MossUtils.GetLayerURI(resource, layerName);

        // Create the location of the header document
        String layerHeaderDocumentURL = MossUtils.GetHeaderDocumentURL(resource, layerName);

        // Request the layer from the gstore
        URL url = new URI(gstoreBaseURL + layerHeaderDocumentURL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(REQ_METHOD_GET);
        connection.setRequestProperty(HEADER_ACCEPT, TEXT_TURTLE);

        // Parse the response to a Jena Model
        Model model = ModelFactory.createDefaultModel();
        RDFParser.source(connection.getInputStream()).forceLang(Lang.TURTLE).parse(model);
      
        MossLayerHeader layerHeader = MossLayerHeader.fromModel(layerURI, model);
        return layerHeader;
    } */

    public MossLayerHeader getOrCreateLayerHeader(String requestBaseURL, String layerName, String resource, Lang language) 
        throws URISyntaxException, MalformedURLException {
        
        // String layerURI = MossUtils.getLayerURI(requestBaseURL, resource, layerName);
        String headerDocumentURL = MossUtils.getHeaderDocumentURL(requestBaseURL, resource, layerName, language);
        String layerURI = headerDocumentURL + LAYER_FRAGMENT;

        MossLayerHeader header = new MossLayerHeader();
        header.setUri(layerURI);
        header.setHeaderDocumentURL(headerDocumentURL);
        header.setDatabusResource(resource);
        header.setLayerName(layerName);

        String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
        header.setCreatedTime(currentTime);

        try {
            // Request the layer from the gstore
            URL url = new URI(headerDocumentURL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(REQ_METHOD_GET);
            connection.setRequestProperty(HEADER_ACCEPT, language.getHeaderString());
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // Parse the response to a Jena Model
                Model model = ModelFactory.createDefaultModel();
                RDFParser.source(connection.getInputStream()).forceLang(language).parse(model); // forceLang(language)

                // Parse values from model
                Resource layerResource = model.getResource(layerURI);
                header.setCreatedTime(RDFUtils.getPropertyValue(model, layerResource, RDFUris.DCT_CREATED));
                // TODO...
            }

        } catch (IOException e) {
            // Layer might not exist yet.
        } catch(PropertyNotFoundException e) {
            
        }

        return header;
    }

    public Model read(String targetURI) throws URISyntaxException, UnsupportedEncodingException {
        Model model = ModelFactory.createDefaultModel();

        try {
            URL url = new URI(targetURI).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(REQ_METHOD_GET);
            connection.setRequestProperty(HEADER_ACCEPT, APPLICATION_LD_JSON);
            connection.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_LD_JSON);

            InputStream inputStream = connection.getInputStream();
            RDFParser.source(inputStream).forceLang(Lang.TURTLE).parse(model);

            inputStream.close();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return model;
    }

    public Model read(String repo, String path) throws URISyntaxException, UnsupportedEncodingException {
        String targetURI = this.gstoreBaseURL + DOCUMENT_READ_ENDPOINT + "?repo=" + repo + "&path=" + path;
        return read(targetURI);
    }


    public void writeHeader(String prefix, MossLayerHeader header, Lang language) throws IOException, URISyntaxException {
        
        logger.debug("Writing header to: {}",  header.getHeaderDocumentURL());

        String headerPath = MossUtils.getDocumentPath(header.getDatabusResource(),
            header.getLayerName(), language);

        StringBuilder uri = new StringBuilder();
        uri.append(gstoreBaseURL)
            .append(DOCUMENT_SAVE_ENDPOINT)
            .append("?")
            .append(REQ_PARAM_REPO)
            .append("=")
            .append(URLEncoder.encode("header", CHAR_ENCODING_UTF8))
            .append("&")
            .append(REQ_PARAM_PATH)
            .append("=")
            .append(URLEncoder.encode(headerPath, CHAR_ENCODING_UTF8))
            .append("&")
            .append(REQ_PARAM_PREFIX)
            .append("=")
            .append(URLEncoder.encode(prefix, CHAR_ENCODING_UTF8));

        URL url = new URI(uri.toString()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(REQ_METHOD_POST);
        connection.setRequestProperty(HEADER_CONTENT_TYPE, language.getHeaderString());
        connection.setDoOutput(true);

        OutputStream outputStream = connection.getOutputStream();
        RDFDataMgr.write(outputStream, header.toModel(), language);
        outputStream.flush();
        outputStream.close();

        // Get response code
        int responseCode = connection.getResponseCode();
        logger.debug("Response code: {}",  responseCode);

        connection.disconnect();
    }

    public void writeContent(String prefix, String path, String rdf, Lang language) throws IOException, URISyntaxException {

        logger.debug("Writing content to: {}",  prefix + path);

        StringBuilder uri = new StringBuilder();
        uri.append(gstoreBaseURL)
            .append(DOCUMENT_SAVE_ENDPOINT)
            .append("?")
            .append(REQ_PARAM_REPO)
            .append("=")
            .append(URLEncoder.encode("content", CHAR_ENCODING_UTF8))
            .append("&")
            .append(REQ_PARAM_PATH)
            .append("=")
            .append(URLEncoder.encode(path, CHAR_ENCODING_UTF8)) 
            .append("&")
            .append(REQ_PARAM_PREFIX)
            .append("=")
            .append(URLEncoder.encode(prefix, CHAR_ENCODING_UTF8));


        URL url = new URI(uri.toString()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(REQ_METHOD_POST);
        connection.setRequestProperty(HEADER_ACCEPT, APPLICATION_LD_JSON);
        connection.setRequestProperty(HEADER_CONTENT_TYPE, language.getHeaderString());
        connection.setDoOutput(true);

        OutputStream outputStream = connection.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, CHAR_ENCODING_UTF8));
        writer.write(rdf);
        writer.flush();
        writer.close();

        // Get response code
        int responseCode = connection.getResponseCode();
        
        logger.debug("Gstore response code: {}", responseCode);

        if(responseCode > 200 && responseCode <= 500) {

            logger.debug("Failed to save RDF: {}", rdf);
            String errorString = readGstoreError(connection);
            connection.disconnect();
            
            throw new IllegalArgumentException(errorString);
        }
        
        connection.disconnect();
    }

    private String readGstoreError(HttpURLConnection connection) throws IOException, UnsupportedEncodingException {
        InputStream inputStream = connection.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHAR_ENCODING_UTF8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        ObjectMapper objectMapper = new ObjectMapper();
        // Parse JSON string to JsonNode
        JsonNode jsonNode = objectMapper.readTree(response.toString());

        // Access a specific field, e.g., address -> city
        String errorMessage = jsonNode.path("message").asText();

        return errorMessage;
    }

    private String readResponse(HttpURLConnection connection) throws IOException, UnsupportedEncodingException {
        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHAR_ENCODING_UTF8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    public void write(String repo, String path, Model model) throws UnsupportedEncodingException, URISyntaxException {
        
        String targetURI = this.gstoreBaseURL + DOCUMENT_SAVE_ENDPOINT + "?repo=" + repo + "&path=" + path;

        try {
            URL url = new URI(targetURI).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(REQ_METHOD_POST);
            connection.setRequestProperty(HEADER_ACCEPT, APPLICATION_LD_JSON);
            connection.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_LD_JSON);
            connection.setDoOutput(true);

            OutputStream outputStream = connection.getOutputStream();
            RDFDataMgr.write(outputStream, model, Lang.JSONLD);
            outputStream.flush();
            outputStream.close();

          
            readResponse(connection);
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> history(String repo, int timespan) {
        List<String> content = new ArrayList<>();
        String uri = this.gstoreBaseURL
                .concat(DOCUMENT_HISTORY_ENDPOINT)
                .concat("?repo=")
                .concat(repo)
                .concat("&limit=")
                .concat(String.valueOf(timespan));

        try {
            URL url = new URI(uri).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(REQ_METHOD_GET);
            connection.setRequestProperty(HEADER_ACCEPT, APPLICATION_LD_JSON);
            connection.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_LD_JSON);

            InputStream inputStream = connection.getInputStream();

            boolean history = this.determineRespone(inputStream);

            if(!history) {
                return new ArrayList<>();
            }

            content = parseHistory(inputStream);

            inputStream.close();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return content;
    }

    private List<String> parseHistory(InputStream inputStream) {
        List<String> historyList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            Gson gson = new Gson();
            JsonElement jsonElement = JsonParser.parseReader(reader);

            if (jsonElement.isJsonArray()) {
                for (JsonElement element : jsonElement.getAsJsonArray()) {
                    historyList.add(gson.toJson(element));
                }
            } else if (jsonElement.isJsonObject()) {
                // Assuming a 400 error response with a message field
                historyList.add(gson.toJson(jsonElement));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return historyList;
    }

    private boolean determineRespone(InputStream inputStream) {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        int firstByte = 1024;
        try {
           bufferedInputStream.mark(firstByte);
           byte[] buffer = new byte[firstByte];
           int bytesRead = bufferedInputStream.read(buffer, 0, firstByte);
           bufferedInputStream.reset();

           if (bytesRead == -1) {
               throw new IOException("Empty input stream");
           }

           char firstChar = (char) buffer[0];
           for (int i = 0; i < bytesRead; i++) {
                firstChar = (char) buffer[i];
                if (!Character.isWhitespace(firstChar)) {
                    break;
                }
           }

           if (firstChar == '{') {
                return true;
           }

           if (firstChar == '{') {
                return false;
           }

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException(inputStream.toString());
    }

   

    
}
