package org.dbpedia.moss.api;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.auth.filters.RequiresPermission;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@Path("/g")
public class MetadataResource {

    @Context
    private HttpServletRequest request;

    @GET
    @Path("{path:.*}")
    @RequiresPermission("read-metadata")
    @Produces({ "text/turtle", "application/ld+json" })
    public Response getMetadataResource(@PathParam("path") String path) {
        String gstoreUrl = gstoreFileUrl(request.getRequestURI());

        try {
            Lang requestedLanguage = RDFLanguages.contentTypeToLang(request.getHeader("Accept"));
            HttpURLConnection connection = (HttpURLConnection) new URI(gstoreUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            if (requestedLanguage != null) {
                connection.setRequestProperty("Accept", requestedLanguage.getHeaderString());
            } else {
                connection.setRequestProperty("Accept", HttpConstants.MediaTypes.APPLICATION_JSON);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Response.status(responseCode)
                        .entity("Failed to fetch the resource from the external server.")
                        .build();
            }

            String fileExtension = gstoreUrl.substring(gstoreUrl.lastIndexOf('.') + 1);
            Lang savedLanguage = RDFLanguages.fileExtToLang(fileExtension);

            if (requestedLanguage != null && requestedLanguage != savedLanguage) {
                try (InputStream inputStream = connection.getInputStream()) {
                    Model model = ModelFactory.createDefaultModel();
                    model.read(inputStream, null, savedLanguage.getName());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    model.write(out, requestedLanguage.getName());
                    return Response.ok(out.toString(), requestedLanguage.getHeaderString()).build();
                }
            }

            try (InputStream inputStream = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                Response.ResponseBuilder builder = Response.ok(out.toString());
                if (requestedLanguage != null) {
                    builder.type(requestedLanguage.getHeaderString());
                }
                return builder.build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Failed to fetch the resource from the external server.")
                    .build();
        }
    }

    static String gstoreFileUrl(String requestUri) {
        if (requestUri.startsWith("/g/")) {
            return ENV.GSTORE_BASE_URL + "/file/" + requestUri.substring(3);
        }
        if (requestUri.startsWith("/g")) {
            return ENV.GSTORE_BASE_URL + "/file/" + requestUri.substring(2);
        }
        return ENV.GSTORE_BASE_URL + requestUri;
    }
}
