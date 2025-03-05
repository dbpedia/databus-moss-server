package org.dbpedia.moss.servlets.indexers;

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
import org.dbpedia.moss.config.MossIndexerConfiguration;
import org.dbpedia.moss.config.MossLayerConfiguration;
import org.dbpedia.moss.utils.Constants;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class IndexerServlet extends HttpServlet {

	private static final String INDEXER_ID_PREFIX = "indexer:";

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(IndexerServlet.class);
	

	@Override
	public void init() throws ServletException {
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		IndexerServletRequestInfo requestInfo = IndexerServletRequestInfo.fromRequest(req);

		if (requestInfo == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found");
			return;
		}

		MossConfiguration mossConfiguration = MossConfiguration.get();
		MossIndexerConfiguration existingIndexer = mossConfiguration.getIndexerByName(requestInfo.getIndexerName());

		if(requestInfo.isConfigRequested()) {
			if(existingIndexer == null) {
				resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Indexer " + requestInfo.getIndexerName() + " does not exist.");
				return;
			}

			// Save the config!
			existingIndexer.createOrFetchConfigFile(mossConfiguration);
			File file = existingIndexer.getConfigFile();

			System.out.println(file.getAbsolutePath());

			try (InputStream inputStream = req.getInputStream()) {
				Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error saving configuration file.");
				e.printStackTrace();
				return;
			}

			resp.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		ObjectMapper objectMapper = new ObjectMapper();
		MossIndexerConfiguration inputIndexer = objectMapper.readValue(req.getReader(), MossIndexerConfiguration.class);
		inputIndexer.setId(INDEXER_ID_PREFIX + requestInfo.getIndexerName());
		inputIndexer.createOrFetchConfigFile(mossConfiguration);

		mossConfiguration.addOrReplaceIndexer(inputIndexer);
		mossConfiguration.save();

		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.setContentType("application/json");
		resp.getWriter().write(objectMapper.writeValueAsString(inputIndexer));
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		IndexerServletRequestInfo requestInfo = IndexerServletRequestInfo.fromRequest(req);

		if (requestInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Path not found");
            return;
        }

		if(requestInfo.isConfigRequested()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Cannot delete indexer configuration. Delete the indexer isntead.");
            return;
		}


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
		IndexerServletRequestInfo requestInfo = IndexerServletRequestInfo.fromRequest(req);

		if (requestInfo == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Path not found");
            return;
        }

		if(requestInfo.isConfigRequested()) {

			// Send the config file
			MossConfiguration mossConfiguration = MossConfiguration.get();
            MossIndexerConfiguration indexer = mossConfiguration.getIndexerByName(requestInfo.getIndexerName());

            if (indexer == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Indexer not found");
                return; 
            }

			indexer.createOrFetchConfigFile(mossConfiguration);
            File configFile = indexer.getConfigFile();

			resp.setContentType("text/plain");
			resp.setCharacterEncoding("UTF-8");

			// Send the contents of the config file
			try (InputStream inputStream = new FileInputStream(configFile)) {
				Files.copy(configFile.toPath(), resp.getOutputStream());
				resp.getOutputStream().flush(); 
			}

		} else {
			String acceptHeader = req.getHeader(Constants.HTTP_HEADER_ACCEPT);
			Lang acceptLanguage = RDFLanguages.contentTypeToLang(acceptHeader);

			if (acceptLanguage == null) {
				acceptLanguage = Lang.JSONLD;
			}

			try {

				MossConfiguration mossConfiguration = MossConfiguration.get();
				MossIndexerConfiguration indexer = mossConfiguration.getIndexerByName(requestInfo.getIndexerName());

				if(indexer == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}

				Model indexerModel = indexer.toModel(ENV.MOSS_BASE_URL);

				if(indexerModel == null) {
					resp.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				
            	MossUtils.sendPrettyRDF(resp, acceptLanguage, indexerModel);

			} catch (IOException e) {
				// Handle internal server error
				resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// Handle bad request
				resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			}
		}
	}

	@Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        getResource(req, resp);
    }
}
