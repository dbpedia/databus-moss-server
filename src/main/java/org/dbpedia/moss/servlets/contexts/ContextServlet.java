package org.dbpedia.moss.servlets.contexts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.dbpedia.moss.config.MossConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class ContextServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(ContextServlet.class);

	public ContextServlet() {

	}

	@Override
	public void init() throws ServletException {
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
	
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		
	}


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		getResource(req, resp);
	}

	public static void getResource(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		MossConfiguration mossConfiguration = MossConfiguration.get();
		File configDir = mossConfiguration.getConfigDir();
		String contextPath = mossConfiguration.getContextPath(); // Relative path like "contexts"

		// Extract requested path relative to /contexts/
		String requestedPath = req.getPathInfo(); // e.g. /example.jsonld
		if (requestedPath == null || requestedPath.equals("/")) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "No file specified");
			return;
		}

		logger.info(configDir.toString());

		// Normalize and avoid path traversal
		File baseDir = new File(configDir, contextPath).getCanonicalFile();
		File requestedFile = new File(baseDir, requestedPath).getCanonicalFile();

		if (!requestedFile.getPath().startsWith(baseDir.getPath())) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
			return;
		}

		if (!requestedFile.exists() || !requestedFile.isFile()) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
			return;
		}

		// Set appropriate content type
		resp.setContentType("application/ld+json");
		resp.setContentLengthLong(requestedFile.length());
		
		// Send the contents of the config file
		try (InputStream inputStream = new FileInputStream(requestedFile)) {
			Files.copy(requestedFile.toPath(), resp.getOutputStream());
			resp.getOutputStream().flush(); 
		}
	}
}
