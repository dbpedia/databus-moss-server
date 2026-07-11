package org.dbpedia.moss.resources;

import org.dbpedia.moss.db.MossRole;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.dbpedia.moss.generated.api.RolesApi;
import org.dbpedia.moss.generated.model.RoleCreateRequest;
import org.dbpedia.moss.generated.model.RolePermissionsRequest;
import org.dbpedia.moss.utils.HttpConstants;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

public class RolesResource implements RolesApi {

    private final UserDatabaseManager userDatabaseManager;

    @Inject
    public RolesResource(UserDatabaseManager userDatabaseManager) {
        this.userDatabaseManager = userDatabaseManager;
    }

    @Override
    public Response listRoles() {
        try {
            String json = new ObjectMapper().writeValueAsString(userDatabaseManager.listRoles());
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response createRole(RoleCreateRequest roleCreateRequest) {
        try {
            userDatabaseManager.createRole(roleCreateRequest.getName(), roleCreateRequest.getTokenRole());
            MossRole role = userDatabaseManager.getRole(roleCreateRequest.getName());
            String json = new ObjectMapper().writeValueAsString(role);
            return Response.status(Response.Status.CREATED)
                    .entity(json)
                    .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response deleteRole(String role) {
        try {
            if (userDatabaseManager.getRole(role) == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            userDatabaseManager.deleteRole(role);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response getRolePermissions(String role) {
        try {
            if (userDatabaseManager.getRole(role) == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            RolePermissionsRequest body = new RolePermissionsRequest();
            body.setPermissions(userDatabaseManager.getRolePermissions(role));
            String json = new ObjectMapper().writeValueAsString(body);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @Override
    public Response setRolePermissions(String role, RolePermissionsRequest rolePermissionsRequest) {
        try {
            if (userDatabaseManager.getRole(role) == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            userDatabaseManager.setRolePermissions(role, rolePermissionsRequest.getPermissions());
            RolePermissionsRequest body = new RolePermissionsRequest();
            body.setPermissions(userDatabaseManager.getRolePermissions(role));
            String json = new ObjectMapper().writeValueAsString(body);
            return Response.ok(json, HttpConstants.MediaTypes.APPLICATION_JSON).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
