package org.dbpedia.moss.requests;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;

public class GstoreConnector {

    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_LD_JSON = "application/ld+json";
    private static final String GRAPH_READ_ENDPOINT = "/graph/read";
    private static final String GRAPH_SAVE_ENDPOINT = "/graph/save";

    private String baseURL;

    public GstoreConnector(String baseURL) {
        this.baseURL = baseURL;
    }

    public Model read(String targetURI) throws URISyntaxException, UnsupportedEncodingException {
        Model model = ModelFactory.createDefaultModel();

        try {
            URL url = new URI(targetURI).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty(ACCEPT_HEADER, APPLICATION_LD_JSON);
            connection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_LD_JSON);

            InputStream inputStream = connection.getInputStream();
            RDFParser.source(inputStream).forceLang(Lang.JSONLD).parse(model);

            inputStream.close();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return model;
    }

    public Model read(String repo, String path) throws URISyntaxException, UnsupportedEncodingException {
        String targetURI = this.baseURL + GRAPH_READ_ENDPOINT + "?repo=" + repo + "&path=" + path;
        return read(targetURI);
    }

    public void write(String repo, String path, Model model) throws UnsupportedEncodingException, URISyntaxException {
        String targetURI = this.baseURL + GRAPH_SAVE_ENDPOINT + "?repo=" + repo + "&path=" + path;

        try {
            URL url = new URI(targetURI).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty(ACCEPT_HEADER, APPLICATION_LD_JSON);
            connection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_LD_JSON);
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
