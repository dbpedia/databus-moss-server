package org.dbpedia.moss.utils;

import org.apache.http.client.utils.URIBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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
}
