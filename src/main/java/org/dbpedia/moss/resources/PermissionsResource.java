package org.dbpedia.moss.resources;

import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.generated.api.PermissionsApi;
import org.dbpedia.moss.utils.HttpConstants;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

public class PermissionsResource implements PermissionsApi {

    private final UserDatabaseManager userDatabaseManager;

    @Inject
    public PermissionsResource(UserDatabaseManager userDatabaseManager) {
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    public Response listPermissions() {
        try {
            String json = new ObjectMapper().writeValueAsString(userDatabaseManager.listPermissions());
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
