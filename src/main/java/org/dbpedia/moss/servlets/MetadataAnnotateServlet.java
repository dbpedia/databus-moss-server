package org.dbpedia.moss.servlets;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.writer.JsonLDWriter;
import org.apache.jena.sparql.core.DatasetGraph;
import org.dbpedia.moss.Main;
import org.dbpedia.moss.requests.RDFAnnotationModData;
import org.dbpedia.moss.requests.RDFAnnotationRequest;
import org.dbpedia.moss.utils.MossConfiguration;
import org.dbpedia.moss.utils.MossUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JacksonInject;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
@WebServlet("/MetadataAnnotateServlet")
@MultipartConfig(location="/tmp")
public class MetadataAnnotateServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataAnnotateServlet.class);

	private MossConfiguration configuration;
    private MetadataService metadataService;

    public MetadataAnnotateServlet(MetadataService service) {
        this.metadataService = service;
    }

	@Override
	public void init() throws ServletException {
		String configPath = getInitParameter(Main.KEY_CONFIG);
		configuration = MossConfiguration.Load();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
        // Set content type
        resp.setContentType("text/html");

        // Create variables to store form data
        String modType = null;
        String databusURI = null;
        String modVersion = null;
        InputStream annotationGraph = null;

        // Get all parts from the request
        Collection<Part> parts = req.getParts();
        for (Part part : parts) {
            // Extract name from form field
            if (part.getName().equals("modType")) {
                modType = req.getParameter("modType");
            }

            if (part.getName().equals("databusURI")) {
                databusURI = req.getParameter("databusURI");
            }

            if (part.getName().equals("modVersion")) {
                modVersion = req.getParameter("modVersion");
            }

            // Extract graph from file upload field
            if (part.getName().equals("annotationGraph")) {
                annotationGraph = part.getInputStream();
            }
        }

        if (modType == null || modVersion == null || databusURI == null || annotationGraph == null) {
            resp.getWriter().println("Malformed Data received.");
            return;
        }

        logInput(modType, modVersion, databusURI, annotationGraph);


        Model annotationModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(annotationModel, annotationGraph, Lang.JSONLD);
        RDFAnnotationRequest request = new RDFAnnotationRequest(
            databusURI,
            modType,
            annotationModel,
            modVersion
        );

        RDFAnnotationModData modData = createRDFAnnotation(request);
        this.metadataService.getIndexerManager().updateIndices(modData.getModType(), modData.getModURI());

        // Send response
        resp.getWriter().println("Data received successfully.");
        resp.getWriter().println(annotationModel.toString());
    }


    public RDFAnnotationModData createRDFAnnotation(RDFAnnotationRequest request) {

        RDFAnnotationModData modData = new RDFAnnotationModData(MossUtils.baseURI, request);
        String modURI = request.getModPath();
        String saveURLRAW = modURI != null ? modURI : modData.getFileURI();

        try {
            MossUtils.saveModel(modData.toModel(), MossUtils.createSaveURL(saveURLRAW));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return modData;
    }


    public void logInput(String modType, String modVersion, String databusURI, InputStream annotationGraph) {
        // Handle the received data
        System.out.println("modType: " + modType);
        System.out.println("modVersion: " + modVersion);
        System.out.println("databusURI: " + databusURI);

        try {
            System.out.println("Annotation Graph size: " + annotationGraph.available());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
