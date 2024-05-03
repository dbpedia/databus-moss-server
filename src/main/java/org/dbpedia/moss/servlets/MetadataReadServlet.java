package org.dbpedia.moss.servlets;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.Main;
import org.dbpedia.moss.utils.MossConfiguration;
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

	private MossConfiguration configuration;

	@Override
	public void init() throws ServletException {
		String configPath = getInitParameter(Main.KEY_CONFIG);
		configuration = MossConfiguration.Load(configPath);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Construct the URL for the request
		String requestURL = this.configuration.getGStoreBaseURL() + req.getRequestURI();
		
		// Create a new HTTP client
		HttpClient httpClient = HttpClient.newHttpClient();
		
		// Create a GET request
		HttpRequest httpRequest = HttpRequest.newBuilder().GET().uri(URI.create(requestURL)).build();
		
		// Send the request and handle the response
		try {
			HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			
			// Get the response body
			String responseBody = httpResponse.body();
			
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
}
