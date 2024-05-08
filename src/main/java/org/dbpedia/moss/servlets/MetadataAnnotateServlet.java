package org.dbpedia.moss.servlets;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Collection;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.writer.JsonLDWriter;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
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
   //  private MetadataService metadataService;

    public MetadataAnnotateServlet() {
        // this.metadataService = service;
    }

	@Override
	public void init() throws ServletException {
		String configPath = getInitParameter(Main.KEY_CONFIG);
		configuration = MossConfiguration.Load();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
        // Set content type
        resp.setContentType("application/ld+json");

        // Create variables to store form data
        String layerName = null;
        String databusURI = null;
        String layerVersion = null;
        InputStream documentStream = null;

        // Get all parts from the request
        Collection<Part> parts = req.getParts();

        for (Part part : parts) {
            // Extract name from form field
            if (part.getName().equals("layerName")) {
                layerName = req.getParameter("layerName");
            }

            if (part.getName().equals("databusURI")) {
                databusURI = req.getParameter("databusURI");
            }

            if (part.getName().equals("layerVersion")) {
                layerVersion = req.getParameter("layerVersion");
            }

            // Extract graph from file upload field
            if (part.getName().equals("document")) {
                documentStream = part.getInputStream();
            }
        }

        if (layerName == null || layerVersion == null || databusURI == null || documentStream == null) {
            resp.getWriter().println("Malformed Data received.");
            return;
        }

        logInput(layerName, layerVersion, databusURI, documentStream);


        // TASKS:
        
        // 1 - Parse input as jena model
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, documentStream, Lang.JSONLD);

        // 2 - Create mod header

        // a) Check if header info already there
        // TODO:

        // b) If not - add annotation mod header to model
        addLayerHeader(model, layerName, databusURI, layerVersion);


        /*
        RDFAnnotationRequest request = new RDFAnnotationRequest(
            databusURI,
            modType,
            model,
            modVersion
        );

        RDFAnnotationModData modData = createRDFAnnotation(request);
        // this.metadataService.getIndexerManager().updateIndices(modData.getModType(), modData.getModURI());
        */

        // Send response
        resp.getWriter().println("Data annotated successfully.");
        resp.getWriter().println(model.toString());
    }


    private void addLayerHeader(Model model, String layerName, String databusDistributionUri, String modVersion) {

        // Eintrag auf Databus
        // --> hat distributions == Databus-Distribution-URI
        // EXAMPLE: https://databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15#jenkins.txt
        // modified*: "databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15/jenkins.txt"
        String databusDistributionUriFragments = MossUtils.getMossDocumentUriFragments(databusDistributionUri);

        // Document im MOSS 
        // --> hat URI == MOSS-Document-URI
        // --> Besteht aus: MOSS-Base-URI + "/g/" + (modified*)Databus-Distribution-URI + modType + fileEnding
        // EXAMPLE: http://localhost:8080/g/databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15/jenkins.txt/simple.jsonld
        String mossDocumentURI = MossUtils.getMossDocumentUri(
            configuration.getMossBaseUrl(), 
            databusDistributionUriFragments, 
            layerName, "jsonld");

        // MOD-Header im MOSS-Document -> ist eine Entity IM Document
        // --> hat eine URI == MOD-URI
        // --> Besteht aus MOSS-Document-URI + "#mod"
        // EXAMPLE: http://localhost:8080/g/databus.dbpedia.org/kikiriki/jenkins/jenkins/2024-05-07-15/jenkins.txt/simple.jsonld#mod
        String layerHeaderURI = mossDocumentURI + "#layer";


        // http://localhost:8080/api/types/OEMetadataMod
        // <> <subclassOf> <http://dataid.dbpedia.org/ns/mod#DatabusMod>
        // if(modTypeUri == null) {
        //    modTypeUri = configuration.getMossBaseUrl() + "/ontology/" + layerName;
        // }

        Resource databusDistributionResource = ResourceFactory.createResource(databusDistributionUri);
        Resource layerHeaderResource = ResourceFactory.createResource(layerHeaderURI);
        Resource layerTypeResource = ResourceFactory.createResource("http://dataid.dbpedia.org/ns/moss#DatabusMetadataLayer");
        Resource mossDocumentResource = ResourceFactory.createResource(mossDocumentURI);
       
        String provNamespace = "http://www.w3.org/ns/prov#";
        String modNamespace = "http://dataid.dbpedia.org/ns/mod#";

        // model.setNsPrefixes(this.nameSpaces);
        Literal time = ResourceFactory.createTypedLiteral(new XSDDateTime(Calendar.getInstance()));

        model.add(layerHeaderResource, RDF.type, layerTypeResource);
        model.add(layerHeaderResource, ResourceFactory.createProperty(modNamespace, "name"), layerName);
        model.add(layerHeaderResource, ResourceFactory.createProperty(modNamespace, "version"), modVersion);
        model.add(layerHeaderResource, ResourceFactory.createProperty(provNamespace, "used"), databusDistributionResource);
        model.add(layerHeaderResource, ResourceFactory.createProperty(provNamespace, "startedAtTime"), time);
        model.add(layerHeaderResource, ResourceFactory.createProperty(provNamespace, "endedAtTime"), time);
        model.add(layerHeaderResource, ResourceFactory.createProperty(provNamespace, "generated"), mossDocumentResource);
        // model.add(modTypeResource, RDFS.subClassOf, ResourceFactory.createResource(modNamespace + "DatabusMod"));
    }

    /*
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

 */
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
