package org.dbpedia.moss.servlets;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.utils.MossUtils;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;


public class MetadataValidateServlet extends HttpServlet {

    public MetadataValidateServlet() {
    }

	@Override
	public void init() throws ServletException {
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            String requestBaseURL = MossUtils.getRequestBaseURL(req);
            // Read stream to string

            String contentTypeHeader = req.getContentType();
            ContentType contentType = ContentType.create(contentTypeHeader);
            Lang language = RDFLanguages.contentTypeToLang(contentType);

            if(language == null) {
                throw new ValidationException("Unknown RDF format for content type " + contentType);
            }
 
            DatabusMetadataLayerData.parse(requestBaseURL, req.getInputStream(), language);
            resp.setStatus(200);

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
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            return;
        }
    }
}
