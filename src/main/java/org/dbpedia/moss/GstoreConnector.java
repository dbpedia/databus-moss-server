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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class GstoreConnector {

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
    private static final String REQ_AUTHOR_NAME = "author_name";

    private String gstoreBaseURL;

    public GstoreConnector(String baseURL) {
        this.gstoreBaseURL = baseURL;
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

    public void write(String baseURL, String repo, String path, String json, String author) throws UnsupportedEncodingException {

        StringBuilder uri = new StringBuilder();
        uri.append(gstoreBaseURL)
            .append(DOCUMENT_READ_ENDPOINT)
            .append("?")
            .append(REQ_PARAM_REPO)
            .append("=")
            .append(URLEncoder.encode(repo, CHAR_ENCODING_UTF8))
            .append("&")
            .append(REQ_PARAM_PATH)
            .append("=")
            .append(URLEncoder.encode(path, CHAR_ENCODING_UTF8))
            .append(REQ_AUTHOR_NAME)
            .append("=")
            .append(URLEncoder.encode(author, CHAR_ENCODING_UTF8));

        System.out.println("SAVING " + json);
        String response = this.writeRequest(uri.toString(), json);
        System.out.println("Response body\n" + response);
    }

    public void write(String baseURL, String repo, String path, String json) throws UnsupportedEncodingException {

        StringBuilder uri = new StringBuilder();
        uri.append(gstoreBaseURL)
            .append(DOCUMENT_READ_ENDPOINT)
            .append("?")
            .append(REQ_PARAM_REPO)
            .append("=")
            .append(URLEncoder.encode(repo, CHAR_ENCODING_UTF8))
            .append("&")
            .append(REQ_PARAM_PATH)
            .append("=")
            .append(URLEncoder.encode(path, CHAR_ENCODING_UTF8));

        System.out.println("SAVING " + json);
        String response = this.writeRequest(uri.toString(), json);
        System.out.println("Response body\n" + response);
    }

    private String writeRequest(String uri, String json) {
        try {
            URL url = new URI(uri).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(REQ_METHOD_POST);
            connection.setRequestProperty(HEADER_ACCEPT, APPLICATION_LD_JSON);
            connection.setRequestProperty(HEADER_CONTENT_TYPE, APPLICATION_LD_JSON);
            connection.setDoOutput(true);

            OutputStream outputStream = connection.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, CHAR_ENCODING_UTF8));
            writer.write(json);
            writer.flush();
            writer.close();

            // Get response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read response body
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, CHAR_ENCODING_UTF8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();

            return response.toString();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (URISyntaxException uriSyntaxException) {
            uriSyntaxException.printStackTrace();
        }

        return null;
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

            InputStream inputStream = connection.getInputStream();
            inputStream.close();

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
