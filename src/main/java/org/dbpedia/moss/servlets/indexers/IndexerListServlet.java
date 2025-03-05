package org.dbpedia.moss.servlets.indexers;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossIndexerConfiguration;
import org.dbpedia.moss.utils.Constants;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class IndexerListServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(IndexerListServlet.class);

	private MossConfiguration mossConfiguration;
	
	
	@Override
	public void init() throws ServletException {

        mossConfiguration = MossConfiguration.get();
	}

    /*
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        
        ObjectMapper objectMapper = new ObjectMapper();
        MossIndexerConfiguration indexer;

        try {
            // Parse JSON request body
            indexer = objectMapper.readValue(req.getReader(), MossIndexerConfiguration.class);
        } catch(Exception e) {
            String message = e.getMessage();
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, message);
            return;
        }

        // Validate fields
        if (indexer.getId() == null || indexer.getId().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"name is required\"}");
            return;
        }

        if(mossConfiguration.getLayerByName(indexer.getId()) != null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.sendError(400, "A layer with that name already exists");
            return;
        }

        mossConfiguration.getIndexers().add(indexer);
        mossConfiguration.save();


        try {


            // Here, you would typically save the layer to a database (not shown)

            // Return created response
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setContentType("application/json");
            resp.getWriter().write(objectMapper.writeValueAsString(indexer));


        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"Failed to process request\"}");
        }
    } */

	 @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Create a model to hold all layer graphs
        Model listModel = ModelFactory.createDefaultModel();

        try {
            // Base URL for layers
            String baseURL = ENV.MOSS_BASE_URL; 

            // Loop through all layers and convert them to RDF
            for (MossIndexerConfiguration indexer : mossConfiguration.getIndexers()) {
                Model indexerModel = indexer.toModel(baseURL); // Convert each layer to RDF
                listModel.add(indexerModel); // Add the layer model to the aggregated model
            }

            // Set the response type to the desired format (defaulting to JSON-LD)
            String acceptHeader = req.getHeader(Constants.HTTP_HEADER_ACCEPT);
            Lang acceptLanguage = RDFLanguages.contentTypeToLang(acceptHeader);

            if (acceptLanguage == null) {
                acceptLanguage = Lang.JSONLD; // Default to JSON-LD if no specific language is provided
            }

            MossUtils.sendPrettyRDF(resp, acceptLanguage, listModel);
			

        } catch (IOException e) {
            // Handle any exceptions
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

   

}
