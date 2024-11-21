package org.dbpedia.moss.servlets;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.indexer.MossLayer;
import org.dbpedia.moss.utils.MossEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LayerTemplateServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerTemplateServlet.class);

	private MossConfiguration mossConfiguration;

	@Override
	public void init() throws ServletException {
		MossEnvironment environment = MossEnvironment.get();

		File configFile = new File(environment.GetConfigPath());
        mossConfiguration = MossConfiguration.fromJson(configFile);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Get the layer name from the request parameter
		String layerName = req.getParameter("layerName");
		
		// Retrieve the layer object from the configuration
		MossLayer layer = mossConfiguration.getLayerByName(layerName);

		if (layer == null) {
			// Layer not found, send a 404 Not Found response
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			resp.getWriter().write("Layer not found.");
		} else {
			// Layer found, load the SHACL file
			File templateFile = new File(layer.getTemplatePath());

			if (!templateFile.exists()) {
				// SHACL file doesn't exist, send 404 Not Found
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				resp.getWriter().write("Template file not found for the specified layer.");
			} else {
				// SHACL file exists, return it as a Turtle (RDF) file
				resp.setContentType(layer.getFormatMimeType());
				try (PrintWriter writer = resp.getWriter()) {
					// Read the SHACL file and send its content as response
					FileInputStream inputStream = new FileInputStream(templateFile);
					byte[] buffer = new byte[1024];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) != -1) {
						writer.write(new String(buffer, 0, bytesRead));
					}
					inputStream.close();
				}
			}
		}
	}
}
