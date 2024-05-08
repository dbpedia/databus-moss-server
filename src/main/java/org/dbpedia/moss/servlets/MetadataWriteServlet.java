package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
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

    private static final String REQ_PARAM_DOCUMENT = "document";
    
    private static final String REQ_PARAM_PATH = "path";

    private static final String TMP_BASE_URL = "http://tmp.org";

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
            InputStream documentStream = null;

            // Get all parts from the request
            Collection<Part> parts = req.getParts();

            for (Part part : parts) {
                if (part.getName().equals(REQ_PARAM_DOCUMENT)) {
                    documentStream = part.getInputStream();
                }

                if (part.getName().equals(REQ_PARAM_PATH)) {
                    documentStream = part.getInputStream();
                }
            }


            // PATH sein wie: /janni/dbpedia-databus/meta1.jsonld

            // Read stream to string
            String jsonString = MossUtils.readToString(documentStream);

            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, jsonString, TMP_BASE_URL, Lang.JSONLD);
        
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
            DatabusMetadataLayerData layerData = new DatabusMetadataLayerData();
            
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
                String documentURI = layerData.GetDocumentURI(configuration.getMossBaseUrl());
                System.out.println("Saving and indexing layer data to " + documentURI);

                // Write unchanged json string
                gstoreConnector.write(documentURI, jsonString);
                
                // Update indices
                indexerManager.updateIndices(layerData.getName(), layerData.GetURI(configuration.getMossBaseUrl()));

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
}
