package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.io.InputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.DatabusMetadataLayerData;
import org.dbpedia.moss.utils.MossUtils;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;


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
            String jsonString = MossUtils.readToString(req.getInputStream());

            InputStream inputStream = new ByteArrayInputStream(jsonString.getBytes(StandardCharsets.UTF_8));
            DatabusMetadataLayerData.parse(requestBaseURL, inputStream);
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
