package org.dbpedia.moss.utils;

import org.apache.http.client.utils.URIBuilder;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.servlets.ValidationException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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


    


    public static String getMossDocumentUri(String mossBaseUrl, String databusDistributionUriFragments,
            String layerName, String fileExtension) {
        return mossBaseUrl + "/g/" + databusDistributionUriFragments + layerName.toLowerCase() + fileExtension;
    }

    public static String readToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }

        reader.close();

        String result = stringBuilder.toString().replaceAll("^\s+|\s+$", "");
        return result;
    }

    public static String getRequestBaseURL(HttpServletRequest req) {
          // Get the protocol (http or https)
          String protocol = req.getScheme();

          // Get the server name
          String serverName = req.getServerName();

          // Get the server port
          int serverPort = req.getServerPort();

          // Construct the base URL
          String baseURL = protocol + "://" + serverName;
          if ((protocol.equals("http") && serverPort != 80) || (protocol.equals("https") && serverPort != 443)) {
              baseURL += ":" + serverPort;
          }

          return baseURL;
    }


    public static String pruneSlashes(String value) {
        if(value == null) {
            return null;
        }
        
        while(value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}

        while(value.startsWith("/")) {
            value = value.substring(1);
        }

        return value;
    }


    public static boolean isValidResourceURI(String resourceUri) {
        try {
            new URI(resourceUri).toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public static String getGStoreRepo(String resourceUri) throws MalformedURLException, URISyntaxException {
        URL resourceURL = new URI(resourceUri).toURL();
        return resourceURL.getHost();
    }

    public static String noTrailingSlash(String path) {

        while(path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }


    public static String getGStorePath(String resourceURI, String layerName)
    throws MalformedURLException, URISyntaxException {
        resourceURI = resourceURI.replace("#", "%23");
        URL resourceURL = null;
        resourceURL = new URI(resourceURI).toURL();
        String path = resourceURL.getPath();
        return path + "/" + layerName;
    }

    public static String createDocumentURI(String base, String repo, String path) {

        if(repo.startsWith("/")) {
            repo = repo.substring(1);
        }

        if(repo.endsWith("/")) {
            repo.substring(0, repo.length() - 1);
        }

        if(path.startsWith("/")) {
            path = path.substring(1);
        }

        return base + "/g/" + repo + "/" + path;
    }

    

    public static String getExtensionURI(String baseUrl, String resource, String layerName) 
    throws MalformedURLException, URISyntaxException {
        String databusResourceURIFragments = MossUtils.getMossDocumentUriFragments(resource);
        return baseUrl + "/res/" + databusResourceURIFragments + "/" + layerName;
    }

    public static String getMossDocumentUriFragments(String resourceURI) throws MalformedURLException, URISyntaxException  {
        resourceURI = resourceURI.replace("#", "%23");

        URL resourceURL = null;
        resourceURL = new URI(resourceURI).toURL();
        String host = resourceURL.getHost();
        String path = resourceURL.getPath();
        String url = host + path;

        return url;
    }

    /**
     * Gets the URI of the layer header document
     * @param resource
     * @param layerName
     * @return
     * @throws URISyntaxException 
     * @throws MalformedURLException 
     */
    public static String getHeaderStoragePath(String resource, String layerName, Lang language) 
    throws MalformedURLException, URISyntaxException {
        return "/header/" + getDocumentStoragePath(resource, layerName, language);
    }

    public static String getContentStoragePath(String resource, String layerName, Lang language) 
        throws MalformedURLException, URISyntaxException {
        return "/content/" + getDocumentStoragePath(resource, layerName, language);
    }

    public static String getContentGraphURI(String baseUrl, String resource, String layerName, Lang language) 
        throws MalformedURLException, URISyntaxException {
        return  baseUrl + "/g/content/" + getDocumentStoragePath(resource, layerName, language);
    }

    public static String getDocumentStoragePath(String resource, String layerName, 
        Lang language) throws MalformedURLException, URISyntaxException {
        // Replace # and %23 with /
        String normalizedResource = resource.replace("#", "/").replace("%23", "/");
        
        // Ensure no double slashes at the end, normalize consecutive slashes
        normalizedResource = normalizedResource.replaceAll("/+", "/");
        
        // Get the file extension from the language
        String extension = language.getFileExtensions().getFirst();
        
        // Get the resource URI fragments
        String databusResourceURIFragments = MossUtils.getMossDocumentUriFragments(normalizedResource);
        
        // Construct the final path
        String resultPath = databusResourceURIFragments + "/" + layerName + "." + extension;
        
        // Ensure no double slashes at the end
        resultPath = resultPath.replaceAll("/+", "/");
        
        // Remove the final slash if it's the last character
        if (resultPath.endsWith("/")) {
            resultPath = resultPath.substring(0, resultPath.length() - 1);
        }

        if (resultPath.startsWith("/")) {
            resultPath = resultPath.substring(1);
        }
        
        return resultPath;
    }

    public static String getPropertyValue(Model model, Resource resource, String propertyURI) {
        // Get the statement corresponding to the property URI from the resource
        Statement statement = resource.getProperty(model.createProperty(propertyURI));
        
        // Check if the statement exists and if the object is a resource (not a literal)
        if (statement != null && statement.getObject().isResource()) {
            // Return the URI of the resource object
            return statement.getObject().asResource().getURI();
        }
    
        // Return null if the property is not found or the object is not a resource
        return null;
    }


    public static String readToString(File configFile) {
        StringBuilder configString = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                configString.append(line).append("\n"); // Preserve line breaks
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle file reading error properly in production
        }

        return configString.toString();
    }

    public static Lang getContentTypeLang(HttpServletRequest req) throws ValidationException {
        String contentTypeHeader = req.getContentType();
        ContentType contentType = ContentType.create(contentTypeHeader);

        if(contentType == null) {
            throw new ValidationException("Unknown Content Type: " +  req.getContentType());
        }

        Lang language = RDFLanguages.contentTypeToLang(contentType);

        if(language == null) {
            throw new ValidationException("Unknown RDF format for content type " + contentType);
        }

        return language;
    }

    public static Lang getAcceptLang(HttpServletRequest req, Lang defaultLang)  {
        String acceptHeader = req.getHeader(Constants.HTTP_HEADER_ACCEPT);
    
        if (acceptHeader != null && !acceptHeader.isEmpty()) {
            Lang parsedLang = RDFLanguages.contentTypeToLang(ContentType.create(acceptHeader));
            if (parsedLang != null) {
                return parsedLang;
            }
        }
        return defaultLang;
    }

    public static void sendPrettyRDF(HttpServletResponse resp, Lang acceptLanguage, Model layerModel) throws IOException {
		
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(acceptLanguage.getHeaderString());

        if(acceptLanguage == Lang.JSONLD) {
		    // Compact
		    JsonObject compacted = RDFUtils.compact(layerModel);
		  
		    try (OutputStream outputStream = resp.getOutputStream();
		        JsonWriter jsonWriter = Json.createWriter(outputStream)) {
		        jsonWriter.write(compacted);
		    } catch (IOException e) {
		        throw new RuntimeException("Error writing JSON response", e);
		    }

		} else {
		    // Write the aggregated model to the output stream
		    try (OutputStream outputStream = resp.getOutputStream()) {
		        layerModel.write(outputStream, acceptLanguage.getName());
		    }
		}
	}


    public static String uriToName(String layerURI) {
        int lastSlashIndex = layerURI.lastIndexOf("/");
        
        // Ensure there is a valid slash and return the substring after it
        if (lastSlashIndex != -1 && lastSlashIndex < layerURI.length() - 1) {
            return layerURI.substring(lastSlashIndex + 1);
        }
        
        // Return an empty string or handle cases where no slash is found
        return layerURI;
    }
    
}