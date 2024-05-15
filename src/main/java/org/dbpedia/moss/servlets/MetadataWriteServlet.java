package org.dbpedia.moss.servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String REQ_PARAM_PATH = "path";

    private static final String REQ_REPO = "repo";

	final static Logger logger = LoggerFactory.getLogger(MetadataWriteServlet.class);

	private MossEnvironment configuration;

    private GstoreConnector gstoreConnector;

    private IndexerManager indexerManager;

	public MetadataWriteServlet(IndexerManager indexerManager) {
        this.indexerManager = indexerManager;
    }

    @Override
	public void init() throws ServletException {
		configuration = MossEnvironment.Get();
        gstoreConnector = new GstoreConnector(configuration.getGstoreBaseURL());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {

            String requestBaseURL = getRequestBaseURL(req);
            // PATH sein wie: /janni/dbpedia-databus/meta1.jsonld
            String repo = req.getParameter(REQ_REPO);
            String path = req.getParameter(REQ_PARAM_PATH);

            // TODO: Validate path (darf nicht mit / enden usw);

            // Read stream to string
            String jsonString = MossUtils.readToString(req.getInputStream());

            System.out.println("REQ Repo: " + repo);
            System.out.println("REQ Path: " + path);
            System.out.println(jsonString);

            String documentURL = CreateDocumentURI(requestBaseURL, repo, path); 

            System.out.println("Future doc URL: " + documentURL);

            InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));

            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, inputStream, documentURL, Lang.JSONLD);
        
            Resource metadataLayerType = ResourceFactory.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER);
            ExtendedIterator<Statement> metadataLayerStatements = model.listStatements(null, RDF.type, metadataLayerType);

            if(!metadataLayerStatements.hasNext()) {
                resp.setStatus(400);
                return;
            }

            Statement statement = metadataLayerStatements.next();
            
            // Remember to close the iterator
            metadataLayerStatements.close();

            Resource resource = statement.getSubject();
            DatabusMetadataLayerData layerData = new DatabusMetadataLayerData(resource.toString());
            
            // Do something with the resource
            System.out.println("Resource of type " + metadataLayerType + ": " + resource);

            // Read all triples that have this resource as a subject
            StmtIterator stmtIterator = model.listStatements(resource, null, (RDFNode) null);

            while (stmtIterator.hasNext()) {
                Statement triple = stmtIterator.next();
                RDFNode object = triple.getObject();
                String predicateURI = triple.getPredicate().getURI();

                // Do something with the triple
                System.out.println("Triple: " + triple);


                    // Check the predicate of the triple and set the corresponding field in layerData
                if (predicateURI.equals(RDFUris.MOSS_NAME)) {
                    layerData.setName(object.toString());
                    continue;
                }
                if (predicateURI.equals(RDFUris.MOSS_VERSION)) {
                    layerData.setVersion(object.toString());
                    continue;
                }
                if (predicateURI.equals(RDFUris.PROV_USED)) {
                    layerData.setDatabusURI(object.toString());
                    continue;
                }
            }
            
            stmtIterator.close();

            if(layerData.isValid()) {

                // Write unchanged json string
                gstoreConnector.write(requestBaseURL, repo, path, jsonString);
                
                // Update indices
                indexerManager.updateIndices(layerData.getUri(), layerData.getName());

            } else {
                System.out.println("Invalid layer data: " + layerData.toString());
                resp.setStatus(400);
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            resp.setStatus(400);
            return;
        }
        
        resp.setStatus(200);
    }

    private String CreateDocumentURI(String base, String repo, String path) {

        if(repo.startsWith("/")) {
            repo = repo.substring(1);
        }

        if(repo.endsWith("/")) {
            repo.substring(0, repo.length() - 1);
        }

        if(path.startsWith("/")) {
            path = path.substring(1);
        }

        return base + "/g/" + repo + "/" + path;   
    }

    private String getRequestBaseURL(HttpServletRequest req) {
          // Get the protocol (http or https)
          String protocol = req.getScheme();
        
          // Get the server name
          String serverName = req.getServerName();
          
          // Get the server port
          int serverPort = req.getServerPort();
          
          // Construct the base URL
          String baseURL = protocol + "://" + serverName;
          if ((protocol.equals("http") && serverPort != 80) || (protocol.equals("https") && serverPort != 443)) {
              baseURL += ":" + serverPort;
          }

          return baseURL;
    }
}
