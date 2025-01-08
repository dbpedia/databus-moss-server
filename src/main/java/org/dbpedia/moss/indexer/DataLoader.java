package org.dbpedia.moss.indexer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.utils.HttpClientWithProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParser;

/**
 * Loads data into the database and runs an indexer on startup
 */
public class DataLoader {

    final static Logger logger = LoggerFactory.getLogger(IndexerManager.class);

    private static final String REQ_METHOD_GET = "GET";

    private GstoreConnector gstoreConnector;
    private String collectionURI;
    private LayerIndexer indexer;

    public DataLoader(DataLoaderConfig config, GstoreConnector gstoreConnector,
            String configRootPath, String lookupBaseURL) {
        this.gstoreConnector = gstoreConnector;
        this.collectionURI = config.getCollectionURI();
        this.indexer = new LayerIndexer(config.getIndexer(), configRootPath, lookupBaseURL);
    }

    private String[] loadCollectionFileURIs() throws URISyntaxException {

        try {   
            URI uri = new URI(collectionURI);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();

            HttpClient httpClient = HttpClientWithProxy.create();
            // Get SPARQL query from the collection
            HttpGet collectionQueryGet = new HttpGet(collectionURI);
            collectionQueryGet.addHeader("Accept", "text/sparql");
            HttpResponse response = httpClient.execute(collectionQueryGet);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String sparqlQueryFromCollection = EntityUtils.toString(entity);

                // Run SPARQL query against SPARQL endpoint
                HttpPost sparqlHttpPost = new HttpPost(baseUrl + "/sparql");

                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("query", sparqlQueryFromCollection));
                sparqlHttpPost.setEntity(new UrlEncodedFormEntity(params));

                HttpResponse sparqlResponse = httpClient.execute(sparqlHttpPost);
                HttpEntity sparqlEntity = sparqlResponse.getEntity();

                if (sparqlEntity != null) {
                    String result = EntityUtils.toString(sparqlEntity);
                    return extractFileURIs(result);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String[] extractFileURIs(String jsonResponse) {
        List<String> fileUris = new ArrayList<>();

        JsonElement jsonElement = JsonParser.parseString(jsonResponse);

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        JsonObject resultsObject = jsonObject.getAsJsonObject("results");
        JsonArray bindingsArray = resultsObject.getAsJsonArray("bindings");

        for (JsonElement element : bindingsArray) {
            JsonObject binding = element.getAsJsonObject();
            JsonObject fileObject = binding.getAsJsonObject("file");
            String uri = fileObject.get("value").getAsString();
            fileUris.add(uri);
        }

        return fileUris.toArray(new String[0]);
    }

    public Model read(String targetURI) throws URISyntaxException, UnsupportedEncodingException {
        Model model = ModelFactory.createDefaultModel();

        try {
            URL url = new URI(targetURI).toURL();

            Proxy proxy = HttpClientWithProxy.getProxy(url.getProtocol());

            HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
            connection.setRequestMethod(REQ_METHOD_GET);
            InputStream inputStream = connection.getInputStream();
            RDFParser.source(inputStream).forceLang(Lang.TURTLE).parse(model);
            inputStream.close();
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return model;
    }

    public void load() {

        // get collection file uris
        try {
            String[] fileURIs = loadCollectionFileURIs();

            if (fileURIs != null) {

                for (int i = 0; i < fileURIs.length; i++) {

                    logger.info("Loading {}...", fileURIs[i]);
                    Model model = cleanModel(read(fileURIs[i]));
                    URI fileURI = new URI(fileURIs[i]);

                    String filePath = fileURI.getPath();
                    String newPath = filePath.substring(0, filePath.lastIndexOf('.')) + ".jsonld";
                    String gstorePath = fileURI.getHost() + newPath;

                    logger.debug("Saving to {}...", gstorePath);
                    gstoreConnector.write("loaded", gstorePath, model);
                }
            }

            indexer.run(null);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static boolean containsNonUnicode(String str) {
        if (str == null) {
            return false;
        }

        return !Pattern.matches("\\A\\p{ASCII}*\\z", str);
    }

    private Model cleanModel(Model model) {
        Model cleanedModel = ModelFactory.createDefaultModel();

        StmtIterator iterator = model.listStatements();

        while (iterator.hasNext()) {
            Statement statement = iterator.next();

            if (containsNonUnicode(statement.getSubject().getURI())) {
                continue;
            }

            if (containsNonUnicode(statement.getPredicate().getURI())) {
                continue;
            }

            if (statement.getObject().isResource()) {
                if (containsNonUnicode(statement.getObject().asResource().getURI())) {
                    continue;
                }
            }

            cleanedModel.add(statement);
        }

        return cleanedModel;
    }
}