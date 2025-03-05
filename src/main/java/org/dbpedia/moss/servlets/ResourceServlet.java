package org.dbpedia.moss.servlets;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.RDF;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossLayerConfiguration;
import org.dbpedia.moss.utils.ENV;
import org.dbpedia.moss.utils.GstoreResource;
import org.dbpedia.moss.utils.MossUtils;
import org.dbpedia.moss.utils.RDFUris;
import org.dbpedia.moss.utils.RDFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class ResourceServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(ResourceServlet.class);

	@Override
	public void init() throws ServletException {
	}


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		String requestURI =	req.getRequestURI();
		logger.info(requestURI);

		Lang contentTypeLanguage = MossUtils.getAcceptLang(req, Lang.JSONLD);
		String requestPath = requestURI.substring(5);
		String extension = Lang.JSONLD.getFileExtensions().getFirst();
		
		String headerDocumentPath = String.format("/header/%s.%s", requestPath, extension);
		try {
			GstoreResource headerDocument = new GstoreResource(headerDocumentPath);
			Model headerModel = headerDocument.readModel(Lang.JSONLD);
			Resource resource = headerModel.getResource(ENV.MOSS_BASE_URL + requestURI);

			String contentGraphURI = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_CONTENT, null);
			String layerURI = RDFUtils.getPropertyValue(headerModel, resource, RDFUris.MOSS_INSTANCE_OF, null);
			String layerName = MossUtils.uriToName(layerURI);
			MossLayerConfiguration layerConfig = MossConfiguration.get().getLayerByName(layerName);
			Lang contentLang = RDFLanguages.contentTypeToLang(layerConfig.getFormatMimeType());

			String contentDocumentPath = contentGraphURI.replace(String.format("%s/g/", ENV.MOSS_BASE_URL), "");
			GstoreResource contentDocument = new GstoreResource(contentDocumentPath);
			Model contentModel = contentDocument.readModel(contentLang);

			Model combinedModel = ModelFactory.createDefaultModel();
			combinedModel.add(headerModel);
			combinedModel.add(contentModel);

			MossUtils.sendPrettyRDF(resp, contentTypeLanguage, combinedModel);

		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
            resp.setHeader("Content-Type", "application/json");
            resp.sendError(400, e.getMessage());
            return;
		}


	}
}
