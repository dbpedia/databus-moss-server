package org.dbpedia.moss.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.dbpedia.moss.db.APIKeyInfo;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;


public class UserDatabaseServlet extends HttpServlet {

	private static final long serialVersionUID = 102831973239L;

	final static Logger logger = LoggerFactory.getLogger(UserDatabaseServlet.class);

    private UserDatabaseManager userDatabase;

    public UserDatabaseServlet(UserDatabaseManager sqliteConnector) {
        this.userDatabase = sqliteConnector;
    }

    @Override
	public void init() throws ServletException {

	}

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String requestURI = req.getRequestURI();
        String sub = (String)req.getAttribute("sub");

        System.out.println(requestURI);
        String operation = getAPIOperation(requestURI);
		
        switch (operation) {
            case "set-username":
                handleSetUsername(sub, req, res);
                break;
            case "create-apikey":
                handleCreateAPIKey(sub, req, res);
                break;
            case "revoke-apikey":
                handleRevokeAPIKey(sub, req, res);
                break;
        }
    }

	private void handleRevokeAPIKey(String sub, HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String keyName = req.getParameter("name");
            userDatabase.deleteAPIKey(sub, keyName);
            res.setStatus(200);

        } catch(SQLException exception) {
            res.getWriter().write(exception.getMessage());
            res.setStatus(400);
        }
    }

    private void handleCreateAPIKey(String sub, HttpServletRequest req, HttpServletResponse res) throws IOException {
        String apiKey = APIKeyValidator.createAPIKey(sub);
        String salt = BCrypt.gensalt();

        System.out.println("Why so salty: " + salt);
        String hashedAPIKey = BCrypt.hashpw(apiKey, salt);
        
        String keyName = req.getParameter("name");

        try {
            userDatabase.insertAPIKey(keyName, sub, hashedAPIKey);

            APIKeyInfo apiKeyInfo = new APIKeyInfo();
            apiKeyInfo.SetKey(apiKey);
            apiKeyInfo.setName(keyName);
    
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(apiKeyInfo);
    
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write(json);
            res.setStatus(200);

        } catch(SQLException exception) {
            res.getWriter().write(exception.getMessage());
            res.setStatus(400);
        }

      
    }

    private String getAPIOperation(String requestURI) {
        String[] segments = requestURI.split(("/"));
        return segments[segments.length - 1];
    }

    private void handleSetUsername(String sub, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String username = req.getParameter("username");
            System.out.println("SET USERNAME: " + sub + " // " + username);
            userDatabase.updateUsername(sub, username);
            resp.setStatus(200);
        } catch (Exception e) {
            resp.getWriter().write(e.getMessage());
            resp.setStatus(400);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String requestURI = req.getRequestURI();
        String sub = (String)req.getAttribute("sub");

        System.out.println(requestURI);
        String operation = getAPIOperation(requestURI);
		
        switch (operation) {
            case "get-user":
                handleGetUser(sub, req, res);
                break;
        }
    }

    private void handleGetUser(String sub, HttpServletRequest req, HttpServletResponse res) throws IOException {
        UserInfo userInfo = userDatabase.getUserInfoBySub(sub);

        if(userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setSub(sub);
        }
        
        List<String> apiKeyNames = userDatabase.getAPIKeyNamesBySub(sub);
        userInfo.setApiKeys(apiKeyNames.toArray(new String[0]));

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(userInfo);

        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(json);
    }
}
