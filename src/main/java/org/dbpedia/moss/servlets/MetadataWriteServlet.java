package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

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
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.requests.DatabusMetadataLayerData;
import org.dbpedia.moss.requests.GstoreConnector;
import org.dbpedia.moss.utils.MossConfiguration;
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

	final static Logger logger = LoggerFactory.getLogger(MetadataWriteServlet.class);

	private MossConfiguration configuration;
    // private MetadataService metadataService;

    private GstoreConnector gstoreConnector;

    private IndexerManager indexerManager;

	public MetadataWriteServlet(IndexerManager indexerManager) {
        this.indexerManager = indexerManager;
    }

    @Override
	public void init() throws ServletException {
		configuration = MossConfiguration.Load();
        gstoreConnector = new GstoreConnector(configuration.getGstoreBaseURL());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("text/html");

        try {
            InputStream documentStream = null;

            // Get all parts from the request
            Collection<Part> parts = req.getParts();

            for (Part part : parts) {
                if (part.getName().equals("document")) {
                    documentStream = part.getInputStream();
                }
            }

            // Read stream to string
            String jsonString = MossUtils.readToString(documentStream);

            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, jsonString, Lang.JSONLD);
        
            Resource metadataLayerType = ResourceFactory.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER);
            ExtendedIterator<Statement> metadataLayerStatements = model.listStatements(null, RDF.type, metadataLayerType);

            // Iterate over each resource of the specified type
            while (metadataLayerStatements.hasNext()) {

                Statement statement = metadataLayerStatements.next();
                Resource resource = statement.getSubject();
                DatabusMetadataLayerData layerData = new DatabusMetadataLayerData();
               
                // Do something with the resource
                System.out.println("Resource of type " + metadataLayerType + ": " + resource);

                // Read all triples that have this resource as a subject
                StmtIterator stmtIterator = model.listStatements(resource, null, (RDFNode) null);
                while (stmtIterator.hasNext()) {
                    Statement triple = stmtIterator.next();
                    // Do something with the triple
                    System.out.println("Triple: " + triple);

                  
                }
                
                stmtIterator.close();

                indexerManager.updateIndices(layerData.getName(), layerData.GetURI());
                gstoreConnector.write(layerData.GetURI(), jsonString);
            }

            // Remember to close the iterator
            metadataLayerStatements.close();

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Do something with the request body string
        // String json = requestBody.toString();
        // this.metadataService.saveMod(json);
    }
}
