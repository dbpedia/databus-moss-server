package org.dbpedia.moss;

import java.io.BufferedReader;
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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.dbpedia.moss.utils.MossEnvironment;

public class GstoreConnector {

    private static final String REQ_METHOD_POST = "POST";
    private static final String REQ_METHOD_GET = "GET";
    private static final String CHAR_ENCODING_UTF8 ="UTF-8";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_LD_JSON = "application/ld+json";
    private static final String GRAPH_READ_ENDPOINT = "/graph/read";
    private static final String GRAPH_SAVE_ENDPOINT = "/graph/save";
    private static final String REQ_PARAM_PREFIX = "prefix";
    private static final String REQ_PARAM_REPO = "repo";
    private static final String REQ_PARAM_PATH = "path";
    private static final String THE_G = "/g/";

    private String gstoreBaseURL;
    private MossEnvironment environment;

    public GstoreConnector(String baseURL) {
        this.gstoreBaseURL = baseURL;
        
        environment = MossEnvironment.Get();
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
        String targetURI = this.gstoreBaseURL + GRAPH_READ_ENDPOINT + "?repo=" + repo + "&path=" + path;
        return read(targetURI);
    }

    public void write(String baseURL, String repo, String path, String json) throws UnsupportedEncodingException, URISyntaxException {

        try {

            String uri = gstoreBaseURL + GRAPH_SAVE_ENDPOINT;
            uri += "?" + REQ_PARAM_PREFIX + "=" + URLEncoder.encode(baseURL + THE_G, CHAR_ENCODING_UTF8);
            uri += "&" + REQ_PARAM_REPO + "=" + URLEncoder.encode( repo, CHAR_ENCODING_UTF8);
            uri += "&" + REQ_PARAM_PATH + "=" +  URLEncoder.encode(path, CHAR_ENCODING_UTF8);

            System.out.println("SAVING " + json);

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
            System.out.println("Response Body: " + response.toString());
         
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void write(String repo, String path, Model model) throws UnsupportedEncodingException, URISyntaxException {
        String targetURI = this.gstoreBaseURL + GRAPH_SAVE_ENDPOINT + "?repo=" + repo + "&path=" + path;

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
}
