package org.dbpedia.moss.servlets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.MossUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class MetadataBrowseServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataBrowseServlet.class);


	@Override
	public void init() throws ServletException {
        // gstoreConnector = new GstoreConnector(configuration.getGstoreBaseURL());
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Construct the URL for the request
		String requestURL = ENV.GSTORE_BASE_URL + req.getRequestURI();
		requestURL = requestURL.replace(ENV.GSTORE_BASE_URL + "/file",
			ENV.GSTORE_BASE_URL + "/file/content");

		HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();

		// Create a GET request
		HttpRequest httpRequest = HttpRequest.newBuilder().GET().uri(URI.create(requestURL)).build();

		// Send the request and handle the response
		try {
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			// Get the response body
			String responseBody = httpResponse.body();
			// Get the content type from the HTTP response headers
			String contentType = httpResponse.headers().firstValue("Content-Type").orElse(null);

			if (contentType != null && contentType.contains("text/html")) {
				responseBody = this.parseFolderRequest(requestURL, responseBody);
				resp.setContentType("application/json");
			} else {
				// Set the content type of the servlet response
				Lang lang =	RDFLanguages.resourceNameToLang(requestURL);

				if(lang != null) {
					resp.setContentType(lang.getContentType().toHeaderString());
				}
			}

			PrintWriter writer = resp.getWriter();

			if(responseBody.startsWith("Requesting")) {
				resp.setStatus(404);
				resp.setContentType("text/html");
				writer.println("Not Found");
				return;
			}

			// Write the response body to the servlet response
			writer.println(responseBody);

		} catch (IOException | InterruptedException e) {
			// Handle any exceptions
			e.printStackTrace();
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while processing the request.");
		}
	}

	public String parseFolderRequest(String baseUrl, String responseBody) throws IOException, InterruptedException   {

		baseUrl = MossUtils.pruneSlashes(baseUrl);
		HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();

		Document doc = Jsoup.parse(responseBody);
		Elements removeParentTag = doc.select("a:contains(Parent Directory)");

		for (Element element : removeParentTag) {
			element.remove();
		}

		Elements tableData = doc.getElementsByTag("a");
		ArrayList<String> files = new ArrayList<>();
		ArrayList<String> folders = new ArrayList<>();

		for(Element element : tableData) {

			String fileName = element.text();

			if ("Parent Directory".equals(fileName)) {
				continue;
			}
			if (".git/".equals(fileName)) {
				continue;
			}

			String url = baseUrl + "/" + fileName;
			HttpRequest httpRequest = HttpRequest.newBuilder().HEAD().uri(URI.create(url)).build();

			HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            String contentType = response.headers().firstValue("Content-Type").orElse("");

            if (contentType.contains("text/html")) {
                folders.add(fileName);
            } else {
                files.add(fileName);
            }
		}


		JSONObject jsonObject = new JSONObject();
		jsonObject.put("files", files);
		jsonObject.put("folders", folders);
		return jsonObject.toString();
	}
}
