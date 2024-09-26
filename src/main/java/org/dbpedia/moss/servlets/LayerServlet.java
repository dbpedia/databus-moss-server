package org.dbpedia.moss.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.indexer.MossLayerType;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.utils.MossUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LayerServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerServlet.class);

	private MossConfiguration mossConfiguration;

	private final String layerPrefix = "/layer";

	@Override
	public void init() throws ServletException {
		MossEnvironment environment = MossEnvironment.get();

		File configFile = new File(environment.GetConfigPath());
        mossConfiguration = MossConfiguration.fromJson(configFile);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Path to the JSON file in resources
		System.out.println(req.getRequestURI());
		String requestURI = req.getRequestURI();
		String layerName = requestURI.replace(layerPrefix, "");

		layerName =	MossUtils.pruneSlashes(layerName);

		if(layerName.length() == 0) {
			sendLayerList(req, resp);
			return;
		}

		MossLayerType layer = null;
		
		for(int i = 0; i < mossConfiguration.getLayers().size(); i++) {
			MossLayerType l = mossConfiguration.getLayers().get(i);

			if(l.getName().equals(layerName)) {
				layer = l;
				break;
			}
		}

		if(layer == null) {
			resp.sendError(404);
			return;
		}

		String jsonFilePath = "/layer-template.jsonld";

		try (InputStream inputStream = getClass().getResourceAsStream(jsonFilePath)) {
			
			if (inputStream == null) {
				throw new IOException("Resource not found: " + jsonFilePath);
			}

			// Read JSON from input stream
			String jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

			// Replace stuff here ...

			// Set response content type to application/json
			resp.setContentType("application/json");
			resp.setCharacterEncoding("UTF-8");

			// Write JSON string to the response
			resp.getWriter().write(jsonString);

		} catch (IOException e) {
			logger.error("Error reading JSON file", e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to read JSON file");
		}
	}

	private void sendLayerList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// Send the request and handle the response
		try {

            String requestBaseURL = MossUtils.getRequestBaseURL(req);
			ArrayList<JSONObject> layers = new ArrayList<>();

			for(int i = 0; i < mossConfiguration.getLayers().size(); i++) {
				MossLayerType layer = mossConfiguration.getLayers().get(i);
				JSONObject layerObject = new JSONObject();

				layerObject.put("name", layer.getName());
				layerObject.put("uri", requestBaseURL + layerPrefix + "/" + layer.getName());

				int dotIndex = layer.getTemplate().lastIndexOf(".");
				
				if(dotIndex >= 0) {
					layerObject.put("format", layer.getTemplate().substring(dotIndex + 1));
				}

				layerObject.put("template", layer.getTemplateContent());
				
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
