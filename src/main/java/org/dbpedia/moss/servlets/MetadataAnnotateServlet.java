package org.dbpedia.moss.servlets;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.Main;
import org.dbpedia.moss.utils.MossConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class MetadataAnnotateServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(MetadataAnnotateServlet.class);

	private MossConfiguration configuration;

	@Override
	public void init() throws ServletException {
		String configPath = getInitParameter(Main.KEY_CONFIG);
		configuration = MossConfiguration.Load();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
        /*
        
        INPUTS

        String databusURI = req.getParameter("databusURI");
        String modType = req.getParameter("modType");
        String modVersion = req.getParameter("modVersion");

        // Extract MultipartFile
        MultipartFile annotationGraph = null;
        Map<String, String[]> parameters = req.getParameterMap();
        MultiMap<String> multiMap = new MultiMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            multiMap.add(entry.getKey(), entry.getValue()[0]);
        }
        MultipartInputStreamProvider multipartInputStreamProvider = new MultipartInputStreamProvider(
                multiMap, request.getInputStream(), request.getContentType());
        multipartInputStreamProvider.parseRequest();
        annotationGraph = multipartInputStreamProvider.getMultipartFile("annotationGraph");
		


        // Same old:

        // Parsen in Jena Model

        // Annotation Header dazu packen

        // Annotierte Metadaten zurückgeben in response (input + moss-header)




        // 8===>














		
		  // Extract request parameters
        String modURI = req.getParameter("modURI");

  

		Model annotationModel = ModelFactory.createDefaultModel();

        try {
            InputStream annotationGraphInputStream = annotationGraph.getInputStream();
            RDFDataMgr.read(annotationModel, annotationGraphInputStream, Lang.JSONLD);
            RDFAnnotationRequest request = new RDFAnnotationRequest(databusURI,
                    modType,
                    annotationModel,
                    modVersion,
                    modURI);

            RDFAnnotationModData modData = metadataService.createRDFAnnotation(request);
            this.metadataService.getIndexerManager().updateIndices(modData.getModType(), modData.getModURI());
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return null; */
	}

	
	/*
    public RDFAnnotationModData createRDFAnnotation(RDFAnnotationRequest request) {

        RDFAnnotationModData modData = new RDFAnnotationModData(this.baseURI, request);
        // FIXME: if modPath also required for model creation -> pass it into the
        // RDFAnnotationModData constructor
        String modURI = request.getModPath();
        String saveURLRAW = modURI != null ? modURI : modData.getFileURI();

        try {
            saveModel(modData.toModel(), createSaveURL(saveURLRAW));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return modData;
    }

    public URL createSaveURL(String annotationFileURI) throws MalformedURLException {
        String path = annotationFileURI.replaceAll(baseURI, "");
        String uriString = this.gStoreBaseURL + path;
        return URI.create(uriString).toURL();
    }

    public String getGStoreBaseURL() {
        return this.gStoreBaseURL;
    }

    private void saveModel(Model annotationModel, URL saveUrl) throws IOException {

        System.out.println("Saving with " + saveUrl.toString());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final JsonLDWriteContext ctx = new JsonLDWriteContext();

        ctx.setJsonLDContext(this.contextJson);
        ctx.setJsonLDContextSubstitution("\"" + this.contextURL + "\"");

        DatasetGraph datasetGraph = DatasetFactory.create(annotationModel).asDatasetGraph();
        JsonLDWriter writer = new JsonLDWriter(RDFFormat.JSONLD_COMPACT_PRETTY);
        writer.write(outputStream, datasetGraph, null, null, ctx);


        String jsonString = outputStream.toString("UTF-8");
        System.out.println("jsonjsonjsonjsonjsonjsonjsonjsonjsonjson");
        System.out.println(jsonString);
        System.out.println("jsonjsonjsonjsonjsonjsonjsonjsonjsonjson");

        HttpURLConnection con = (HttpURLConnection) saveUrl.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept", "application/ld+json");
        con.setRequestProperty("Content-Type", "application/ld+json");

        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }
    }
	*/
}
