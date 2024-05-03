package org.dbpedia.moss.utils;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

public final class DatabusUtils {

    public static int checkIfValidDatabusId(String databusID) {

        String databusBase = MossUtils.extractBaseFromURL(databusID);

        if (databusBase == null) {
            return -1;
        }

        String idRegex = "^" + Pattern.quote(databusBase) + "(/[^/]+){1,5}";
        if (!databusID.matches(idRegex))
            return 0;

        CookieHandler.setDefault(new CookieManager());
        try {
            URI uri = URI.create(databusID);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(uri).header("Accept", "application/*").method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 400)
                return 1;
            else
                return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
