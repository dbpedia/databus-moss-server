package org.dbpedia.moss.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;

import org.dbpedia.moss.MossConfiguration;
import org.dbpedia.moss.utils.MossEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;

import org.db.SqliteConnector;


public class DBServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(LayerServlet.class);

	private MossConfiguration mossConfiguration;
	private final String dbPrefix = "/db";

    @Override
	public void init() throws ServletException {
		MossEnvironment environment = MossEnvironment.Get();

		File configFile = new File(environment.GetConfigPath());
        mossConfiguration = MossConfiguration.fromJson(configFile);
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Path to the JSON file in resources
		System.out.println(req.getRequestURI());
		String requestURI = req.getRequestURI();
        requestURI = requestURI.replace(dbPrefix, "");

        switch (requestURI) {
            case "/create-user-table":
                SqliteConnector.createUserTable();
                break;
            case "/create-api-table":
                SqliteConnector.createAPITable();
                break;
            case "/get-users":
                SqliteConnector.getUsers();
                break;
            case "/insert-user":
                String username = req.getParameter("user");
                String sub = req.getParameter("sub");
                SqliteConnector.insertUser(sub, username);
                break;
            default:
                System.out.println("DB Route!");
                break;
        }

	}

}
