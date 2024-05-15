package org.dbpedia.moss.utils;

import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.writer.JsonLDWriter;
import org.apache.jena.sparql.core.DatasetGraph;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MossUtils {

    public static final Pattern baseRegex = Pattern.compile("^(https?://[^/]+)");

    // this is intentionally without any # or / ending since json2rdf always appends # to the base uri
    public static final String json_rdf_base_uri = "http://mods.tools.dbpedia.org/ns/demo";
    public static final String baseURI = "https://databus.dbpedia.org";
    public static String contextURL = "https://raw.githubusercontent.com/dbpedia/databus-moss/dev/devenv/context.jsonld";


    public static String getValFromArray(String[] str_array) {
        if (str_array == null) {
            return "";
        } else {
            return str_array[0];
        }
    }
    

    public static String createAnnotationFileURI(String baseURL, String modType, String databusIdentifier) {
        List<String> pathSegments = new ArrayList<String>();

        databusIdentifier = databusIdentifier.replaceAll( "http[s]?://", "");
        String[] resourceSegments = databusIdentifier.split("/");

        pathSegments.add("g");

        for (String segment : resourceSegments) {
            pathSegments.add(segment);
        }

        String fileName = modType.toLowerCase() + ".jsonld";
        pathSegments.add(fileName);

        return buildURL(baseURL, pathSegments);
    }


     public static String buildURL(String baseURL, List<String> pathSegments) {
        String identifier = "";
        try {
            URIBuilder builder = new URIBuilder(baseURL);
            builder.setPathSegments(pathSegments);

            identifier = builder.build().toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return  identifier;
    }


    public static String extractBaseFromURL(String uri) {
        Matcher m = baseRegex.matcher(uri);

        String result;


        if (m.find()) {
            result = m.group(1);
        } else {
            result = null;
        }
        return result;
    }

    public static boolean dateIsInRange(LocalDate date, LocalDate startDate, LocalDate endDate) {

        if (startDate != null && endDate != null) {
            // if both set ->
        } else if (startDate == null && endDate != null) {

        } else if (startDate != null) {

        } else {
            // if both not set -> everything is in range
            return true;
        }
        return false;
    }


    public static URL createSaveURL(String annotationFileURI) throws MalformedURLException {
        MossEnvironment config = MossEnvironment.Get();
        String path = annotationFileURI.replaceAll(MossUtils.baseURI, "");
        String gStoreBaseURL = config.getGstoreBaseURL();
        String uriString = gStoreBaseURL + path;
        return URI.create(uriString).toURL();
    }

    public static void saveModel(Model annotationModel, URL saveUrl) throws IOException {

        System.out.println("Saving with " + saveUrl.toString());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final JsonLDWriteContext ctx = new JsonLDWriteContext();

        String jsonLDContext = null;
        try {
            jsonLDContext = MossUtils.fetchJSON(MossUtils.contextURL);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        ctx.setJsonLDContext(jsonLDContext);
        ctx.setJsonLDContextSubstitution("\"" + MossUtils.contextURL + "\"");

        DatasetGraph datasetGraph = DatasetFactory.create(annotationModel).asDatasetGraph();
        JsonLDWriter writer = new JsonLDWriter(RDFFormat.JSONLD_COMPACT_PRETTY);
        writer.write(outputStream, datasetGraph, null, null, ctx);

        String jsonString = outputStream.toString("UTF-8");
        System.out.println("jsonjsonjsonjsonjsonjsonjsonjsonjsonjson");
        System.out.println(jsonString);
        System.out.println("jsonjsonjsonjsonjsonjsonjsonjsonjsonjson");

        HttpURLConnection con = (HttpURLConnection) saveUrl.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept", "application/ld+json");
        con.setRequestProperty("Content-Type", "application/ld+json");

        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }
    }


    public static String fetchJSON(String urlString) throws IOException, URISyntaxException {
        StringBuilder result = new StringBuilder();
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } finally {
            connection.disconnect();
        }

        return result.toString();
    }


    public static String getMossDocumentUriFragments(String databusDistributionUri) {
        // TODO
        // databusDistributionUri = "https://databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15#jenkins.txt";
        // databusDistributionUri = "https://databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15";

        // Mache aus
        // "https://databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15#jenkins.txt"

        // Den da
        // "databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15/jenkins.txt"

        // Also z.B: Als URI parsen, protocol weg-cutten, '#' ersetzen mit '/'
        URL databusDistributionURL = null;
        try {
            databusDistributionURL = new URI(databusDistributionUri).toURL();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String host = databusDistributionURL.getHost();
        String path = databusDistributionURL.getPath();
        String fragment = databusDistributionURL.getRef();

        String url = host + path;

        if (fragment == null) {
            return url;
        }

        return url + "/" + fragment;
    }


    public static String getMossDocumentUri(String mossBaseUrl, String databusDistributionUriFragments,
            String modType, String fileExtension) {
        // TODO
        // --> Besteht aus: MOSS-Base-URI + "/g/" + databusDistributionUriFragments + lowercase(modType) + fileEnding
        // throw new UnsupportedOperationException("Unimplemented method 'getMOSSDocumentUri'");
        return mossBaseUrl + "/g/" + databusDistributionUriFragments + modType.toLowerCase() + fileExtension;
    }

    public static String readToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }

        reader.close();
        return stringBuilder.toString();
    }
}