package org.dbpedia.moss;

import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.ServletTester;

public final class TestUtils {

    public static HttpTester.Response send(ServletTester tester, HttpTester.Request request) throws Exception {
        return HttpTester.parseResponse(tester.getResponses(request.generate()));
    }

    public static HttpTester.Request createRequest(String method, String uri, String content) {
        HttpTester.Request req = HttpTester.newRequest();
        req.setMethod(method);
        req.setURI(uri);
        req.setVersion("HTTP/1.1");
        req.setHeader("Host", "tester");

        if (content != null) {
            req.setContent(content);
        }

        return req;
    }

    static HttpTester.Response sendRequest(ServletTester tester, String method, String uri, String content) throws Exception {
        HttpTester.Request req = createRequest(method, uri, content);
        return send(tester, req);
    }

    static HttpTester.Response sendRequest(ServletTester tester, String method, String uri) throws Exception {
        HttpTester.Request req = createRequest(method, uri, null);
        return send(tester, req);
    }

}
