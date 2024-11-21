package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.indexer.MossLayer;
import org.dbpedia.moss.indexer.MossLayerHeader;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class MetadataWriteServlet extends HttpServlet {

	/**
	 *
	 */
	private static final long serialVersionUID = 102831973239L;

    // private static final String REQ_LAYER_NAME = "layer";

    // private static final String REQ_RESOURCE_URI = "resource";


	final static Logger logger = LoggerFactory.getLogger(MetadataWriteServlet.class);

	private MossEnvironment env;

    private GstoreConnector gstoreConnector;

    private IndexerManager indexerManager;

    private UserDatabaseManager userDatabaseManager;
    
    private MossConfiguration mossConfiguration;
    
    public MetadataWriteServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    public void init() throws ServletException {
        env = MossEnvironment.get();
        gstoreConnector = new GstoreConnector(env.getGstoreBaseURL());
        
        File configFile = new File(env.GetConfigPath());
        mossConfiguration = MossConfiguration.fromJson(configFile);
	}


	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {

            UserInfo userInfo = this.getUserInfo(req);

            // Model model = ModelFactory.createDefaultModel();
            // RDFParser.source(req.getInputStream()).forceLang(Lang.JSONLD).parse(model); 
            
            String requestBaseURL = env.getMossBaseUrl(); // MossUtils.getRequestBaseURL(req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang requestLanguage = getRDFLanguage(req);
            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String layerName = req.getParameter("layer");

            MossLayer layer = mossConfiguration.getLayerByName(layerName);
           
            if(layer == null) {
                throw new IllegalArgumentException("Specified layer name is unkown to this MOSS instance:" + layerName);
            }

            Lang layerLanguage = RDFLanguages.contentTypeToLang(layer.getFormatMimeType());

            if(requestLanguage != layerLanguage) {
                throw new IllegalArgumentException("Invalid RDF language used: " + requestLanguage.toLongString() +
                    ". Has to be " + layerLanguage.toLongString() + " for layer "  + layerName + ".");
            }

            validateInputForLayer(layer, rdfString, requestLanguage);
            
            String contentDocumentURL = MossUtils.getContentDocumentURL(requestBaseURL, 
                resource, layerName, requestLanguage);

            System.out.println("Resource: " + resource);
            System.out.println("Layer name: " + layerName);
            System.out.println("Content document: " + contentDocumentURL);
            System.out.println("Language: " + requestLanguage);

            validateResourceForLayer(resource, layer);
                        
            String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);

            MossLayerHeader header = gstoreConnector.getOrCreateLayerHeader(requestBaseURL, layerName, resource, layerLanguage);
            header.setModifiedTime(currentTime);
            header.setLastModifiedBy(userInfo.getUsername());
            header.setContentDocumentURL(contentDocumentURL);

            // Save to gstore!
            String contentDocumentPath = MossUtils.getDocumentPath(resource, layerName, layerLanguage);
            gstoreConnector.writeHeader(requestBaseURL + "/g/", header, layerLanguage);
            gstoreConnector.writeContent(requestBaseURL + "/g/", contentDocumentPath, rdfString, layerLanguage);
            indexerManager.updateIndices(header.getUri(), header.getLayerName());

        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, message);
            return;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        } catch (ValidationException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        } catch (RiotException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            return;
        }

        resp.setStatus(200);
    }

    private void validateResourceForLayer(String resource, MossLayer layer) {

        try {
            String resourceType = layer.getResourceType();
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
                    if (type.getURI().equals(resourceType)) {
                        return;
                    }
                }
            }

            throw new IllegalArgumentException("Databus resource <" + resource + "> does not have the required type <" 
                + resourceType + "> (detected type: <" + foundType + ">).");

        } catch(RiotNotFoundException e) {
            throw new IllegalArgumentException("Databus resource " + resource + " is not reachable");
        }
    }

    private void validateInputForLayer(MossLayer layer, String rdfString, Lang language) throws Exception {

        // Load the SHACL shape model from the file path provided by the layer
        String shaclPath = layer.getShaclPath();

        if(shaclPath == null) {
            return;
        }
        
        Model shaclModel = RDFDataMgr.loadModel(shaclPath, RDFLanguages.TURTLE);

        // Load the RDF data from the provided RDF string
        Model dataModel = ModelFactory.createDefaultModel();

        try (InputStream rdfInput = new ByteArrayInputStream(rdfString.getBytes())) {
            RDFDataMgr.read(dataModel, rdfInput, language);
        }

        // Validate the RDF data model against the SHACL shape model
        ValidationReport report = ShaclValidator.get().validate(shaclModel.getGraph(), dataModel.getGraph());

        // Check if validation passed or failed
        if (!report.conforms()) {
            // Validation failed: throw an exception or handle the report as needed
            throw new IllegalArgumentException("RDF data does not conform to SHACL shapes of layer " + layer.getName() 
                + ": " + report.getEntries().toString());
        } else {
            System.out.println("Validation successful: RDF data conforms to SHACL shapes.");
        }
    }
            
    private Lang getRDFLanguage(HttpServletRequest req) throws ValidationException {
        String contentTypeHeader = req.getContentType();
        ContentType contentType = ContentType.create(contentTypeHeader);

        if(contentType == null) {
            throw new ValidationException("Unknown Content Type: " +  req.getContentType());
        }
        
        System.out.println("Content type:" + contentType.toHeaderString());

        Lang language = RDFLanguages.contentTypeToLang(contentType);

        if(language == null) {
            throw new ValidationException("Unknown RDF format for content type " + contentType);
        }

        System.out.println(language.toLongString());
        return language;
    }

    private UserInfo getUserInfo(HttpServletRequest request) throws ValidationException {
        Object sub = request.getAttribute("sub");

        if (sub == null) {
            throw new ValidationException("sub zero.");
        }

        UserInfo userInfo = userDatabaseManager.getUserInfoBySub(sub.toString());
        System.out.println(userInfo);

        if (userInfo == null || userInfo.getUsername().isEmpty()) {
            throw new ValidationException("User null or username missing");
        }

        return userInfo;
    }

}
