package org.dbpedia.moss.users;

import java.util.Arrays;

import org.dbpedia.moss.generated.api.PermissionsApi;
import org.dbpedia.moss.http.HttpConstants;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;

public class PermissionsResource implements PermissionsApi {

    @Override
    public Response listPermissions() {
        try {
            String json = new ObjectMapper().writeValueAsString(Arrays.asList(Permissions.ALL));
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
