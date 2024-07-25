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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jena.riot.RiotException;
import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.GstoreConnector;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.indexer.IndexerManager;
import org.dbpedia.moss.utils.MossEnvironment;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.servlets.UserDatabaseServlet;

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

	private MossEnvironment configuration;

    private GstoreConnector gstoreConnector;

    private IndexerManager indexerManager;

    private UserDatabaseManager userDatabaseManager;

	public MetadataWriteServlet(IndexerManager indexerManager, UserDatabaseManager userDatabaseManager) {
        this.indexerManager = indexerManager;
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
	public void init() throws ServletException {
		configuration = MossEnvironment.get();
        gstoreConnector = new GstoreConnector(configuration.getGstoreBaseURL());
	}


	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            UserInfo userInfo = this.getUserInfo(req);
            String requestBaseURL = MossUtils.getRequestBaseURL(req);
            String jsonString = MossUtils.readToString(req.getInputStream());

            InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
            DatabusMetadataLayerData layerData = DatabusMetadataLayerData.parse(requestBaseURL, inputStream);

            System.out.println("\ninputt");
            System.out.println(jsonString);

            // Write unchanged json string
            gstoreConnector.write(requestBaseURL, layerData.getRepo(), layerData.getPath(), jsonString, userInfo.getUsername());

            // Update indices
            indexerManager.updateIndices(layerData.getUri(), layerData.getLayerName());

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
