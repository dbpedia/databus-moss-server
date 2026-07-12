package org.dbpedia.moss.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Enumeration;
import java.util.Set;

import org.dbpedia.moss.generated.api.SparqlApi;
import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.http.MutableServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource that proxies SPARQL requests to the configured store endpoint.
 */
public class SparqlResource implements SparqlApi {

    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host",
            "connection",
            "content-length",
            "accept-encoding"
    );

    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "content-length",
            "transfer-encoding",
            "content-encoding",
            "connection"
    );

    @Context
    private HttpServletRequest request;

    private final String sparqlEndpoint = ENV.GSTORE_BASE_URL + "/sparql";

    /**
     * Proxies a GET request to the store SPARQL endpoint, forwarding the query string and headers.
     *
     * @return the proxied SPARQL response
     */
    @Override
    public Response sparqlGet() {
        return proxyRequest(request, HttpConstants.Methods.GET);
    }

    /**
     * Proxies a POST request with a SPARQL query body to the store SPARQL endpoint.
     *
     * @param body the SPARQL query
     * @return the proxied SPARQL response
     */
    @Override
    public Response sparqlPost(String body) {
        // JAX-RS has already consumed the raw body; wrap the request so proxyRequest can stream it again.
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.APPLICATION_SPARQL_QUERY);
        return proxyRequest(req, HttpConstants.Methods.POST);
    }

    /**
     * Forwards an HTTP request to the store SPARQL endpoint and returns the upstream response.
     *
     * @param req    the incoming servlet request
     * @param method the HTTP method to use
     * @return the proxied response, or an error status if the request fails
     */
    private Response proxyRequest(HttpServletRequest req, String method) {
        String queryString = req.getQueryString();
        HttpURLConnection connection;

        try {
            // Append the client's query string so SPARQL GET parameters (e.g. query=, format=) reach the store.
            connection = (HttpURLConnection) new URI(sparqlEndpoint + (queryString != null ? "?" + queryString : "")).toURL()
                    .openConnection();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            connection.setRequestMethod(method);

            // Pass through client headers (Accept, Authorization, etc.) so content negotiation works upstream.
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!SKIP_REQUEST_HEADERS.contains(headerName.toLowerCase())) {
                    connection.setRequestProperty(headerName, req.getHeader(headerName));
                }
            }

            if (HttpConstants.Methods.POST.equalsIgnoreCase(method)) {
                connection.setDoOutput(true);
                // HttpURLConnection requires an explicit output stream before it will send the POST body.
                try (InputStream inputStream = req.getInputStream(); OutputStream outputStream = connection.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // getInputStream() throws on 4xx/5xx; read the error body from getErrorStream() instead.
            try (InputStream inputStream = connection.getInputStream()) {
                copy(inputStream, out);
            } catch (IOException e) {
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        copy(errorStream, out);
                    }
                }
            }

            byte[] body = out.toByteArray();
            Response.ResponseBuilder builder = Response.status(responseCode).entity(body);
            // getHeaderFields() uses a null key for the status line; skip hop-by-hop headers.
            connection.getHeaderFields().forEach((key, values) -> {
                if (key != null && !SKIP_RESPONSE_HEADERS.contains(key.toLowerCase())) {
                    values.forEach(value -> builder.header(key, value));
                }
            });
            return builder.build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    /**
     * Copies all bytes from {@code in} to {@code out}.
     */
    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }
}
