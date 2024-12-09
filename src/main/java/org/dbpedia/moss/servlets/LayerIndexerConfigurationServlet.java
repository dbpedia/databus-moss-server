package org.dbpedia.moss.servlets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.indexer.IndexingTaskConfiguration;
import org.dbpedia.moss.indexer.MossLayer;
import org.dbpedia.moss.utils.MossEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LayerIndexerConfigurationServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerShaclServlet.class);

	private MossConfiguration mossConfiguration;
	
	private String configRootPath;

	@Override
	public void init() throws ServletException {
		MossEnvironment environment = MossEnvironment.get();

		File configFile = new File(environment.GetConfigPath());
		mossConfiguration = MossConfiguration.fromJson(configFile);
		configRootPath = configFile.getParent();
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
		}
	}
}
