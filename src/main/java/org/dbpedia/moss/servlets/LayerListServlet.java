package org.dbpedia.moss.servlets;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.indexer.MossLayer;
import org.dbpedia.moss.utils.MossEnvironment;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LayerListServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerListServlet.class);

	private MossConfiguration mossConfiguration;

	@Override
	public void init() throws ServletException {
		MossEnvironment environment = MossEnvironment.get();

		File configFile = new File(environment.GetConfigPath());
        mossConfiguration = MossConfiguration.fromJson(configFile);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Send the request and handle the response
		try {

			ArrayList<JSONObject> layers = new ArrayList<>();

			for(int i = 0; i < mossConfiguration.getLayers().size(); i++) {
				MossLayer layer = mossConfiguration.getLayers().get(i);
				JSONObject layerObject = new JSONObject();

				Lang language = RDFLanguages.contentTypeToLang(layer.getFormatMimeType());
				String formatExtension = language.getFileExtensions().getFirst();

				layerObject.put("name", layer.getName());
				layerObject.put("format", layer.getFormatMimeType());
				layerObject.put("formatExtension", formatExtension);
				layerObject.put("resourceTypes", layer.getResourceTypes());
				layerObject.put("hasSHACL", layer.getShaclPath() != null);
				layerObject.put("hasTemplate", layer.getTemplatePath() != null);
				layers.add(layerObject);
			}

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("layers", layers);
		
			// Get the response body
			resp.setContentType("application/json");
			String responseBody = jsonObject.toString();
			
			// Write the response body to the servlet response
			PrintWriter writer = resp.getWriter();
			writer.println(responseBody);

		} catch (IOException e) {
			// Handle any exceptions
			e.printStackTrace();
			resp.sendError(500);
		}
	}

}
