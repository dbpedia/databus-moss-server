package org.dbpedia.moss.servlets.layers;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossLayerConfiguration;
import org.dbpedia.moss.utils.Constants;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class LayerServlet extends HttpServlet {

	
	private static final String LAYER_ID_PREFIX = "layer:";

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerListServlet.class);
	

	@Override
	public void init() throws ServletException {
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		MossConfiguration mossConfiguration = MossConfiguration.get();
		String layerName = req.getPathInfo().replace("/", "");
				

		ObjectMapper objectMapper = new ObjectMapper();
		// Parse JSON request body
		MossLayerConfiguration inputLayer = objectMapper.readValue(req.getReader(), MossLayerConfiguration.class);
		inputLayer.setId(LAYER_ID_PREFIX + layerName);

		mossConfiguration.addOrReplaceLayer(inputLayer);
		mossConfiguration.save();
		/*
		if(!layerName.equals(layer.getId())) {
			resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, String.format("Cannot change the layer name: %s.", layerName));
            return;
		} */


		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.setContentType("application/json");
		resp.getWriter().write(objectMapper.writeValueAsString(inputLayer));
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		MossConfiguration mossConfiguration = MossConfiguration.get();
		String layerName = req.getPathInfo().replace("/", "");
		MossLayerConfiguration layerConfiguration =	mossConfiguration.getLayerByName(layerName);

		if(layerConfiguration == null) {
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(404, String.format("Layer %s does not exist.", layerName));
            return;
		}

		boolean removed = mossConfiguration.getLayers().removeIf(layer -> layer.getId().equals(layerName));

		if(!removed) {
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(500, String.format("Failed to remove layer %s.", layerName));
            return;
		}

        mossConfiguration.save();

		
		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.setContentType("application/json");
	}

	public static void getResource(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String layerName = req.getPathInfo().replace("/", "");

        String acceptHeader = req.getHeader(Constants.HTTP_HEADER_ACCEPT);
        Lang acceptLanguage = RDFLanguages.contentTypeToLang(acceptHeader);

        if (acceptLanguage == null) {
            acceptLanguage = Lang.JSONLD;
        }

        try {

			MossConfiguration mossConfiguration = MossConfiguration.get();

			MossLayerConfiguration layer = mossConfiguration.getLayerByName(layerName);

			if(layer == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			Model layerModel = layer.toModel();

			if(layerModel == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			// Set the response if resource is found
		
			MossUtils.sendPrettyRDF(resp, acceptLanguage, layerModel);
			

        } catch (IOException e) {
            // Handle internal server error
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // Handle bad request
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
	}

	

	@Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        getResource(req, resp);
    }
}
