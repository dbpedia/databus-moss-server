package org.dbpedia.moss.servlets;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
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
    private MetadataService metadataService;

    public MetadataWriteServlet(MetadataService service) {
        this.metadataService = service;
    }

	@Override
	public void init() throws ServletException {
		String configPath = getInitParameter(Main.KEY_CONFIG);
		configuration = MossConfiguration.Load();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");

        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }

        String json = requestBody.toString();
        Model annotationModel = ModelFactory.createDefaultModel();
        InputStream targetStream = new ByteArrayInputStream(json.getBytes());
        RDFDataMgr.read(annotationModel, targetStream, Lang.JSONLD);
        String provNamespace = "http://www.w3.org/ns/prov#";
        Property generatedProperty = annotationModel.getProperty(provNamespace + "generated");
        List<Resource> resources = annotationModel.listSubjectsWithProperty(generatedProperty).toList();

        try {
            Resource modURI = resources.get(0);
            URL modSaveURL = MossUtils.createSaveURL(modURI.toString());
            MossUtils.saveModel(annotationModel, modSaveURL);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Do something with the request body string
        // String json = requestBody.toString();
        // this.metadataService.saveMod(json);
    }
}
