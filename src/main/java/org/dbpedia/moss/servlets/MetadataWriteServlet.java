package org.dbpedia.moss.servlets;

import java.io.IOException;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.indexer.MossLayerHeader;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.utils.MossUtils;

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

	public MetadataWriteServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
	public void init() throws ServletException {
		env = MossEnvironment.get();
        gstoreConnector = new GstoreConnector(env.getGstoreBaseURL());
	}


	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {

            UserInfo userInfo = this.getUserInfo(req);

            // Model model = ModelFactory.createDefaultModel();
            // RDFParser.source(req.getInputStream()).forceLang(Lang.JSONLD).parse(model); 
            
            String requestBaseURL = env.getMossBaseUrl(); // MossUtils.getRequestBaseURL(req);
            String rdfString = MossUtils.readToString(req.getInputStream());
            Lang language = getRDFLanguage(req);

            String resource = MossUtils.pruneSlashes(req.getParameter("resource"));
            String layerName = req.getParameter("layer");

            // TODO: Check if layer exists
            
            String contentDocumentURL = MossUtils.getContentDocumentURL(requestBaseURL, 
                resource, layerName, language);

            System.out.println("Resource: " + resource);
            System.out.println("Layer name: " + layerName);
            System.out.println("Content document: " + contentDocumentURL);
            System.out.println("Language: " + language);

            String currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);

            MossLayerHeader header = gstoreConnector.getOrCreateLayerHeader(requestBaseURL, layerName, resource, language);
            header.setModifiedTime(currentTime);
            header.setLastModifiedBy(userInfo.getUsername());
            header.setContentDocumentURL(contentDocumentURL);

            // Save to gstore!
            String contentDocumentPath = MossUtils.getDocumentPath(resource, layerName, language);

            gstoreConnector.writeHeader(requestBaseURL + "/g/", header, language);
            gstoreConnector.writeContent(requestBaseURL + "/g/", contentDocumentPath, rdfString, language);

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
