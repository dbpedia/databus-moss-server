package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossLayerConfiguration;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.indexer.MossLayerHeader;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.MossUtils;

/**
 * Writes to metadata documents
 * Example: /api/write?layerName=openenergy&resource=someurl
 */
public class MetadataWriteServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataWriteServlet.class);

    private IndexerManager indexerManager;

    private UserDatabaseManager userDatabaseManager;
    
    private MossConfiguration mossConfiguration;
    
    public MetadataWriteServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    public void init() throws ServletException {
        mossConfiguration = MossConfiguration.get();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            String requestBaseURL = ENV.MOSS_BASE_URL; 

            UserInfo userInfo = this.getUserInfo(req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang contentTypeLanguage = MossUtils.getContentTypeLang(req);

            // The two important input parameters
            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String layerId = req.getParameter("layer");

            logger.debug("New write request: {}", req.getRequestURL());

            MossLayerConfiguration layer = mossConfiguration.getLayer(layerId);
           
            if(layer == null) {
                throw new IllegalArgumentException("Specified layer name is unkown to this MOSS instance: " + layerId);
            }

            Lang layerLanguage = RDFLanguages.contentTypeToLang(layer.getFormatMimeType());

            if(rdfString == null || rdfString.isBlank()) {
                File templateFile = layer.getTemplateFile();

                if (templateFile != null && templateFile.exists()) {
                    rdfString = Files.readString(templateFile.toPath(), StandardCharsets.UTF_8);
                    rdfString = rdfString.replace(mossConfiguration.getTemplateResourcePlaceholder(), resource);
                }
            }
           
            // Validate the input document with SHACL shapes
            Model model = validateInputForLayer(layer, rdfString, contentTypeLanguage);

             // Layer language *should* be the same as the request language, but doesn't have to
             if(contentTypeLanguage != layerLanguage) {
                // Log warning
                logger.warn("Input RDF language " + contentTypeLanguage.toLongString() 
                    + " is different from the RDF language defined for this layer " + layerLanguage.toLongString() 
                    + ". Automatic conversion will happen.");

                StringWriter out = new StringWriter();
                RDFDataMgr.write(out, model, layerLanguage);
                rdfString = out.toString();
            }
          
            String entryURI = MossUtils.getExtensionURI(requestBaseURL, resource, layer.getName());

            logger.info("Resource: {}", resource);
            logger.info("Entry URI: {}", entryURI);
            logger.info("Storage Language: {}", layerLanguage);

            // validateResourceForLayer(resource, layer);
                        
            String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
           
            // Get the gstore resource for the header 
            String headerDocumentPath = MossUtils.getHeaderStoragePath(resource, layer.getName(), Lang.JSONLD);
            GstoreResource headerDocument = new GstoreResource(headerDocumentPath);

            // Read header document, update fields and write back to header document
            MossLayerHeader header = MossLayerHeader.fromModel(entryURI, headerDocument.readModel(Lang.JSONLD));
            header.setModifiedTime(currentTime);
            header.setLayerURI(layer.getURI());
            header.setDatabusResourceURI(resource);
            header.setContentGraphURI(MossUtils.getContentGraphURI(requestBaseURL, resource, layer.getName(), layerLanguage));
            header.setLastModifiedBy(userInfo.getUsername());

            headerDocument.writeModel(header.toModel(), Lang.JSONLD);

            // Get the gstore resource for the content
            String contentDocumentPath = MossUtils.getContentStoragePath(resource, layer.getName(), layerLanguage);
            GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
            contentDocument.writeDocument(rdfString, layerLanguage);

            indexerManager.updateResource(entryURI, layerId);
            
            // Create JSON response
            Map<String, String> jsonResponse = new HashMap<>();
            jsonResponse.put("path", MossUtils.getDocumentStoragePath(resource, layer.getName(), layerLanguage));

            // Convert to JSON and send response
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponseString = objectMapper.writeValueAsString(jsonResponse);

            resp.setContentType("application/json");
            resp.setStatus(200);
            resp.getWriter().write(jsonResponseString);

        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, message);
            return;
        } catch (UnsupportedEncodingException | URISyntaxException | ValidationException | RiotException e ) {
            logger.error(e.getMessage());
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, e.getMessage());
            return;
        } catch (Exception e) {
            logger.error(e.getMessage());
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(500, e.getMessage());
            return;
        }
    }

    /*
    private void validateResourceForLayer(String resource, MossLayerConfiguration layer) {

        try {
            String[] resourceTypes = layer.getResourceTypes();

            if(resourceTypes == null){
                return;
            }
            
            Model databusModel = RDFDataMgr.loadModel(resource);
            
            Resource rdfResource = databusModel.getResource(resource);
            Property rdfTypeProperty = databusModel.getProperty(RDFUris.RDF_TYPE);
            NodeIterator types = databusModel.listObjectsOfProperty(rdfResource, rdfTypeProperty);
            String foundType = null;

            while (types.hasNext()) {
                RDFNode typeNode = types.next();
                if (typeNode.isResource()) {
                    Resource type = typeNode.asResource();
                    foundType = type.getURI();

                    for(int i = 0; i < resourceTypes.length; i++) {
                        if (type.getURI().equals(resourceTypes[i])) {
                            // Found a matching type!
                            return;
                        }
                    }
                    
                   
                }
            }

            String typesString = String.join(">, <", resourceTypes);
            throw new IllegalArgumentException("Databus resource <" + resource + "> does not have one of the required types: <" 
                + typesString + "> (detected type: <" + foundType + ">).");

        } catch(RiotNotFoundException e) {
            throw new IllegalArgumentException("Databus resource " + resource + " is not reachable");
        }
    } */

    private Model validateInputForLayer(MossLayerConfiguration layer, String rdfString, Lang language) throws Exception {

       // Load the RDF data from the provided RDF string
       Model dataModel = ModelFactory.createDefaultModel();

       try (InputStream rdfInput = new ByteArrayInputStream(rdfString.getBytes())) {
           RDFDataMgr.read(dataModel, rdfInput, language);
       }

       /*
       if(dataModel.isEmpty()) {
            throw new IllegalArgumentException("Writing an empty graph is not allowed.");
       }  */      
       
        // Load the SHACL shape model from the file path provided by the layer
        String shaclPath = layer.getShaclPath();

        if(shaclPath == null) {
            return dataModel;
        }

        Model shaclModel = RDFDataMgr.loadModel(shaclPath, RDFLanguages.TURTLE);

        

        // Validate the RDF data model against the SHACL shape model
        ValidationReport report = ShaclValidator.get().validate(shaclModel.getGraph(), dataModel.getGraph());

        // Check if validation passed or failed
        if (!report.conforms()) {
            // Validation failed: throw an exception or handle the report as needed
            throw new IllegalArgumentException("RDF data does not conform to SHACL shapes of layer " + layer.getId() 
                + ": " + report.getEntries().toString());
        } else {
            logger.debug("Validation successful: RDF data conforms to SHACL shapes.");
        }
        
        return dataModel;
    }
        

    private UserInfo getUserInfo(HttpServletRequest request) throws ValidationException {
        Object sub = request.getAttribute("sub");

        if (sub == null) {
            throw new ValidationException("sub zero.");
        }

        UserInfo userInfo = userDatabaseManager.getUserInfoBySub(sub.toString());

        if (userInfo == null || userInfo.getUsername().isEmpty()) {
            throw new ValidationException("User null or username missing");
        }

        return userInfo;
    }

}
