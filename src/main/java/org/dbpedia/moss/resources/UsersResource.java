package org.dbpedia.moss.resources;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.dbpedia.moss.db.APIKeyInfo;
import org.dbpedia.moss.db.APIKeyValidator;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.db.UserInfo;
import org.dbpedia.moss.generated.api.UsersApi;
import org.dbpedia.moss.generated.model.ApiKeyCreateRequest;
import org.dbpedia.moss.generated.model.SetUsernameRequest;
import org.dbpedia.moss.generated.model.UserRolesRequest;
import org.dbpedia.moss.utils.HttpConstants;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

public class UsersResource implements UsersApi {

    private static final Logger logger = LoggerFactory.getLogger(UsersResource.class);

    @Context
    private HttpServletRequest request;

    private final UserDatabaseManager userDatabaseManager;

    @Inject
    public UsersResource(UserDatabaseManager userDatabaseManager) {
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    public Response listUsers() {
        try {
            String json = new ObjectMapper().writeValueAsString(userDatabaseManager.listUsers());
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response getCurrentUser() {
        String sub = (String) request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        try {
            UserInfo userInfo = userDatabaseManager.getUserInfoBySub(sub);
            if (userInfo == null) {
                userInfo = new UserInfo();
                userInfo.setSub(sub);
            }

            List<String> apiKeyNames = userDatabaseManager.getAPIKeyNamesBySub(sub);
            userInfo.setApiKeys(apiKeyNames.toArray(String[]::new));

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) request.getAttribute(HttpConstants.OIDC.KEY_ROLES);
            if (roles != null) {
                userInfo.setRoles(roles.toArray(String[]::new));
            }

            @SuppressWarnings("unchecked")
            Set<String> permissions = (Set<String>) request.getAttribute(HttpConstants.OIDC.KEY_PERMISSIONS);
            if (permissions != null) {
                userInfo.setPermissions(permissions.toArray(String[]::new));
            }

            String json = new ObjectMapper().writeValueAsString(userInfo);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).encoding("UTF-8").build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response updateCurrentUser(SetUsernameRequest setUsernameRequest) {
        String sub = (String) request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        try {
            userDatabaseManager.updateUsername(sub, setUsernameRequest.getUsername());
            logger.info("User {} set his name to {}", sub, setUsernameRequest.getUsername());
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response createApiKey(ApiKeyCreateRequest apiKeyCreateRequest) {
        String sub = (String) request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        String apiKey = APIKeyValidator.createAPIKey(sub);
        String hashedAPIKey = BCrypt.hashpw(apiKey, BCrypt.gensalt());
        String keyName = apiKeyCreateRequest.getName();

        try {
            userDatabaseManager.insertAPIKey(keyName, sub, hashedAPIKey);

            APIKeyInfo apiKeyInfo = new APIKeyInfo();
            apiKeyInfo.SetKey(apiKey);
            apiKeyInfo.setName(keyName);

            String json = new ObjectMapper().writeValueAsString(apiKeyInfo);
            return Response.status(Response.Status.CREATED)
                    .entity(json)
                    .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                    .encoding("UTF-8")
                    .build();
        } catch (Exception exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
    }

    @Override
    public Response deleteApiKey(String name) {
        String sub = (String) request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        try {
            userDatabaseManager.deleteAPIKey(sub, name);
            return Response.noContent().build();
        } catch (SQLException exception) {
            return Response.status(Response.Status.BAD_REQUEST).entity(exception.getMessage()).build();
        }
    }

    @Override
    public Response getUserRoles(String identifier) {
        try {
            String sub = userDatabaseManager.resolveSub(identifier);
            if (sub == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            UserRolesRequest body = new UserRolesRequest();
            body.setRoles(userDatabaseManager.getUserRoles(sub));
            String json = new ObjectMapper().writeValueAsString(body);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response setUserRoles(String identifier, UserRolesRequest userRolesRequest) {
        try {
            String sub = userDatabaseManager.resolveSub(identifier);
            if (sub == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            userDatabaseManager.setUserRoles(sub, userRolesRequest.getRoles());
            UserRolesRequest body = new UserRolesRequest();
            body.setRoles(userDatabaseManager.getUserRoles(sub));
            String json = new ObjectMapper().writeValueAsString(body);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
