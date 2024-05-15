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
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.dbpedia.moss.utils.MossEnvironment;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class MetadataReadServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataReadServlet.class);

	private MossEnvironment configuration;

	@Override
	public void init() throws ServletException {
		configuration = MossEnvironment.Get();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Construct the URL for the request
		String requestURL = this.configuration.getGstoreBaseURL() + req.getRequestURI();

		// Check file extension
		String fileExtension = FilenameUtils.getExtension(requestURL);
		Boolean noFileExtension = false;

		// if requesting folder change to /file url
		noFileExtension = fileExtension.isEmpty();
		if (noFileExtension) {
			requestURL = requestURL.replace("/g/", "/file/");
		}

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

			if (noFileExtension) {
				responseBody = this.parseFolderRequest(responseBody);
			}

			// Get the content type from the HTTP response headers
			String contentType = httpResponse.headers().firstValue("Content-Type").orElse("application/json");
		
			// Set the content type of the servlet response
			resp.setContentType(contentType);
			
			// Write the response body to the servlet response
			PrintWriter writer = resp.getWriter();
			writer.println(responseBody);

		} catch (IOException | InterruptedException e) {
			// Handle any exceptions
			e.printStackTrace();
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred while processing the request.");
		}
	}

	public String parseFolderRequest(String responseBody) {
		Document doc = Jsoup.parse(responseBody);
		Elements tableData = doc.getElementsByTag("a");

		ArrayList<String> folderList = tableData.stream()
												.filter(element -> !element.text().equals(".git/") &&!element.text().equals("Parent Directory"))
												.map(Element::text)
												.collect(Collectors.toCollection(ArrayList::new));

		folderList.stream().forEach(System.out::println);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("folders", folderList);
		return jsonObject.toString();
	}
}
