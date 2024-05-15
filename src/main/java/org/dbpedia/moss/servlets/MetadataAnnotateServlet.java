package org.dbpedia.moss.servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collection;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.Literal;
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
@WebServlet("/MetadataAnnotateServlet")
@MultipartConfig(location="/tmp")
public class MetadataAnnotateServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataAnnotateServlet.class);

	private MossEnvironment configuration;


    private GstoreConnector gstoreConnector;

    private IndexerManager indexerManager;

    public MetadataAnnotateServlet(IndexerManager indexerManager) {
        this.indexerManager = indexerManager;
    }

	@Override
	public void init() throws ServletException {
		configuration = MossEnvironment.Get();
        gstoreConnector = new GstoreConnector(configuration.getGstoreBaseURL());
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
                layerName = req.getParameter("layerName").toLowerCase();
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

        this.logInput(layerName, layerVersion, databusURI, documentStream);

        // TASKS:
        
        // 1 - Parse input as jena model
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, documentStream, Lang.JSONLD);

        // 2 - Create layer/mod header
        // a) Check if header info already there
        // TODO: Done possibly?

        Boolean layerContained = this.checkForHeader(model);

        // FIXME: this case never evaluates to True, potential bug?
        if (layerContained) {
            resp.getWriter().println("Data already present");
            resp.getWriter().println(model.toString());
            return;
        }

        // b) If not - add annotation mod header to model
        this.addLayerHeader(model, layerName, databusURI, layerVersion);


        // Write json string
        // ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // RDFDataMgr.write(outputStream, model, Lang.JSONLD);
        // System.out.println(outputStream);
        // String jsonString = outputStream.toString();
        // String repo = "databus.openenergyplatform.org";
        // String path = "my/custom/path/oemetadatalayer.jsonld";

        // try {
        //     //FIXME: what is the correct baseURL?
        //     gstoreConnector.write(gstoreConnector.getBaseURL(), repo, path, jsonString);
        // } catch (Exception e) {
        //     System.out.println("error while writing to gstore");
        //     e.printStackTrace();
        // }

        //TODO: implement this...
        // Update indices
        // indexerManager.updateIndices(layerData.getUri(), layerData.getName());

        // Send response
        resp.getWriter().println("Data annotated successfully.");
        resp.getWriter().println(model.toString());
    }

    private boolean checkForHeader(Model model) {
        boolean layerContained = false;
        
        Resource metadataLayerType = ResourceFactory.createResource(RDFUris.MOSS_DATABUS_METADATA_LAYER);
        ExtendedIterator<Statement> metadataLayerStatements = model.listStatements(null, RDF.type, metadataLayerType);

        if(!metadataLayerStatements.hasNext()) {
            return layerContained;
        }

        Statement statement = metadataLayerStatements.next();
        
        // Remember to close the iterator
        metadataLayerStatements.close();

        Resource resource = statement.getSubject();
        // DatabusMetadataLayerData layerData = new DatabusMetadataLayerData();

        // Read all triples that have this resource as a subject
        StmtIterator stmtIterator = model.listStatements(resource, null, (RDFNode) null);

        while (stmtIterator.hasNext()) {
            Statement triple = stmtIterator.next();
            // RDFNode object = triple.getObject();
            String predicateURI = triple.getPredicate().getURI();

            // Do something with the triple
            System.out.println("Triple: " + triple);

            // Check the predicate of the triple and set the corresponding field in layerData
            if (predicateURI.equals(RDFUris.MOSS_DATABUS_METADATA_LAYER)) {
                layerContained = true;
                break;
            }
        }

        stmtIterator.close();
        return layerContained;
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
        String modNamespace = "http://dataid.dbpedia.org/ns/moss#";

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
