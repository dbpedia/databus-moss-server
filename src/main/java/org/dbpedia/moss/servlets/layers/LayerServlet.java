package org.dbpedia.moss.servlets.layers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
		
		LayerServletRequestInfo requestInfo = LayerServletRequestInfo.fromRequest(req);

		MossConfiguration mossConfiguration = MossConfiguration.get();
		MossLayerConfiguration existingLayer = mossConfiguration.getLayerByName(requestInfo.getLayerName());

		if(requestInfo.isResourceRequested()) {
			ObjectMapper objectMapper = new ObjectMapper();
			// Parse JSON request body
			MossLayerConfiguration inputLayer = objectMapper.readValue(req.getReader(), MossLayerConfiguration.class);
			inputLayer.setId(LAYER_ID_PREFIX + requestInfo.getLayerName());
	
			mossConfiguration.addOrReplaceLayer(inputLayer);
			mossConfiguration.save();
			
			resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
			resp.getWriter().write(objectMapper.writeValueAsString(inputLayer));

		} else if (requestInfo.isTemplateRequested()) {

			if(existingLayer == null) {
				resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
				resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Indexer " + requestInfo.getLayerName() + " does not exist.");
				return;
			}

			// Save the config!
			existingLayer.createOrFetchTemplateFile(mossConfiguration);
			File file = existingLayer.getTemplateFile();

			try (InputStream inputStream = req.getInputStream()) {
				Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error saving configuration file.");
				e.printStackTrace();
				return;
			}

            resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
			resp.setStatus(HttpServletResponse.SC_CREATED);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		MossConfiguration mossConfiguration = MossConfiguration.get();
		String layerName = req.getPathInfo().replace("/", "");
		MossLayerConfiguration layerConfiguration =	mossConfiguration.getLayerByName(layerName);

		if(layerConfiguration == null) {
            resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
            resp.sendError(404, String.format("Layer %s does not exist.", layerName));
            return;
		}

		boolean removed = mossConfiguration.getLayers().removeIf(layer -> layer.getName().equals(layerName));

		if(!removed) {
            resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
            resp.sendError(500, String.format("Failed to remove layer %s.", layerName));
            return;
		}

        mossConfiguration.save();

		
		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.setContentType("application/json");
	}

	public static void getResource(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		LayerServletRequestInfo requestInfo = LayerServletRequestInfo.fromRequest(req);

		if(requestInfo == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

        try {

			if(requestInfo.isResourceRequested()) {
				String acceptHeader = req.getHeader(Constants.HTTP_HEADER_ACCEPT);
				Lang acceptLanguage = RDFLanguages.contentTypeToLang(acceptHeader);
		
				if (acceptLanguage == null) {
					acceptLanguage = Lang.JSONLD;
				}
		
		
				MossConfiguration mossConfiguration = MossConfiguration.get();
	
				MossLayerConfiguration layer = mossConfiguration.getLayerByName(requestInfo.getLayerName());
	
				if(layer == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
	
				Model layerModel = layer.toModel();
				MossUtils.sendPrettyRDF(resp, acceptLanguage, layerModel);

			} else if(requestInfo.isTemplateRequested()) {
				// Send the template
				// Send the config file
				MossConfiguration mossConfiguration = MossConfiguration.get();
				MossLayerConfiguration layer = mossConfiguration.getLayerByName(requestInfo.getLayerName());

				if (layer == null) {
					resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
					resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Layer not found");
					return; 
				}

				layer.createOrFetchTemplateFile(mossConfiguration);
				File templateFile = layer.getTemplateFile();

				resp.setContentType("text/plain");
				resp.setCharacterEncoding("UTF-8");

				// Send the contents of the config file
				try (InputStream inputStream = new FileInputStream(templateFile)) {
					Files.copy(templateFile.toPath(), resp.getOutputStream());
					resp.getOutputStream().flush(); 
				}
			} else {
				resp.setHeader(Constants.HTTP_HEADER_CONTENT_TYPE, Constants.HTTP_CONTENT_TYPE_JSON);
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Layer not found");
				return; 
			}

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
