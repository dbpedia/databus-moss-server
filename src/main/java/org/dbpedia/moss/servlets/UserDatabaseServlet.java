package org.dbpedia.moss.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.dbpedia.moss.db.UserDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;


public class UserDatabaseServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(UserDatabaseServlet.class);

	private final String dbPrefix = "/db";

    private UserDatabaseManager sqliteConnector;

    public UserDatabaseServlet(UserDatabaseManager sqliteConnector) {
        this.sqliteConnector = sqliteConnector;
    }

    @Override
	public void init() throws ServletException {

	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Path to the JSON file in resources
		System.out.println(req.getRequestURI());
		String requestURI = req.getRequestURI();
        requestURI = requestURI.replace(dbPrefix, "");

        switch (requestURI) {
            case "/get-users":
                sqliteConnector.getUsers();
                break;
            case "/insert-user":
                String username = req.getParameter("user");
                String sub = req.getParameter("sub");
                sqliteConnector.insertUser(sub, username);
                break;
            default:
                System.out.println("DB Route!");
                break;
        }

	}

}
