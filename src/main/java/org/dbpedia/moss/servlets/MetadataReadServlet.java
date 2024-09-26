package org.dbpedia.moss.servlets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.utils.MossEnvironment;
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
		configuration = MossEnvironment.get();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Construct the URL for the request
		String requestURI = configuration.getGstoreBaseURL() + req.getRequestURI();

		try {

			URL url = new URI(requestURI).toURL();
		
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			// Get the response code
			int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				resp.sendError(responseCode, "Failed to fetch the resource from the external server.");
				return;
			}

			// Determine the content type based on the file extension of the request URI
			String fileExtension = requestURI.substring(requestURI.lastIndexOf('.') + 1);
			Lang language = RDFLanguages.fileExtToLang(fileExtension);

			// Set the content type header for the response
			if(language != null) {
				resp.setContentType(language.getHeaderString());
			}

			// Read the response from the external server
			try (InputStream inputStream = connection.getInputStream();
				OutputStream outputStream = resp.getOutputStream()) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}
		} catch (MalformedURLException e) {
			resp.sendError(400, "Failed to fetch the resource from the external server.");
			e.printStackTrace();
		} catch (URISyntaxException e) {
			resp.sendError(400, "Failed to fetch the resource from the external server.");
			e.printStackTrace();
		}
	}
}
