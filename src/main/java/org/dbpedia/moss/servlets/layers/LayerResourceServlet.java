package org.dbpedia.moss.servlets.layers;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manages layers - creation, modification, deletion, list and retrieval of related documents such as SHACL
 */
public class LayerResourceServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	@Override
	public void init() throws ServletException {
	}


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		LayerServlet.getResource(req, resp);
	}
}
