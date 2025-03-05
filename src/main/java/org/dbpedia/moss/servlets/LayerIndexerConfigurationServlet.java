package org.dbpedia.moss.servlets;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LayerIndexerConfigurationServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerShaclServlet.class);

	

	@Override
	public void init() throws ServletException {

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Get the layer name from the request parameter
		
		// Retrieve the layer object from the configuration

		/*
		String layerName = req.getParameter("layerName");

		MossLayerConfiguration layer = mossConfiguration.getLayerByName(layerName);

		if (layer == null) {
			// Layer not found, send a 404 Not Found response
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			resp.getWriter().write("Layer not found.");
		} else {

			ArrayList<IndexingTaskConfiguration> configurations = new ArrayList<>();

			for(String indexerConfigFile : layer.getIndexers()) {

				// Layer found, load the SHACL file
				File indexerFile = new File(configRootPath + "/" + indexerConfigFile);

				if (!indexerFile.exists()) {
					continue;
				}

				IndexingTaskConfiguration configuration = IndexingTaskConfiguration.fromYaml(indexerFile);
				configuration.setName(indexerConfigFile);
				configurations.add(configuration);

			}
			
				// Set response content type and encoding
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");

			// Use Jackson to convert the list to JSON
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(resp.getWriter(), configurations);
		} */
	}
}
