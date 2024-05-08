package org.dbpedia.moss.servlets;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.writer.JsonLDWriter;
import org.apache.jena.sparql.core.DatasetGraph;
import org.dbpedia.moss.Main;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.requests.GstoreConnector;
import org.dbpedia.moss.requests.RDFAnnotationModData;
import org.dbpedia.moss.requests.RDFAnnotationRequest;
import org.dbpedia.moss.utils.MossConfiguration;
import org.dbpedia.moss.utils.MossUtils;
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
        
            // Get MOSS-Header from model

            // TODO: Get layer name from header
            String layerName = "";

            // TODO: Get layer document URI from header
            String layerURI = "";

            indexerManager.updateIndices(layerName, layerURI);
      
            gstoreConnector.write(layerURI, jsonString);

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
