package org.dbpedia.moss.resources;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.generated.api.GApi;
import org.dbpedia.moss.utils.ENV;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class MetadataResource implements GApi {

    @Context
    private HttpServletRequest request;

    @Override
    public Response getMetadataResource(String path) {
        String requestURI = ENV.GSTORE_BASE_URL + request.getRequestURI();

        try {
            Lang requestedLanguage = RDFLanguages.contentTypeToLang(request.getHeader("Accept"));
            HttpURLConnection connection = (HttpURLConnection) new URI(requestURI).toURL().openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return Response.status(responseCode)
                        .entity("Failed to fetch the resource from the external server.")
                        .build();
            }

            String fileExtension = requestURI.substring(requestURI.lastIndexOf('.') + 1);
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
}
