package org.dbpedia.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.users.Permissions;
import org.dbpedia.moss.users.UserDatabaseManager;
import org.dbpedia.moss.users.UsersResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

public class UsersResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private File databaseFile;
    private UserDatabaseManager userDatabase;
    private UsersResource usersResource;

    @BeforeEach
    public void setUp() throws Exception {
        databaseFile = Files.createTempFile("moss-users-test", ".db").toFile();
        databaseFile.deleteOnExit();
        userDatabase = new UserDatabaseManager(databaseFile.getAbsolutePath());
        usersResource = new UsersResource(userDatabase);
    }

    @AfterEach
    public void tearDown() {
        if (databaseFile != null && databaseFile.exists()) {
            databaseFile.delete();
        }
    }

    @Test
    public void getCurrentUserWithoutAuthReturnsAnonymousPrincipal() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT)).thenReturn(null);
        when(request.getAttribute(HttpConstants.OIDC.KEY_ROLES))
                .thenReturn(userDatabase.resolveAnonymousRoles());
        when(request.getAttribute(HttpConstants.OIDC.KEY_PERMISSIONS))
                .thenReturn(userDatabase.resolveAnonymousPermissions());
        HandlerTestSupport.bindRequest(usersResource, request);

        Response response = usersResource.getCurrentUser();

        assertEquals(200, response.getStatus());
        JsonNode body = MAPPER.readTree(HandlerTestSupport.entityAsJson(response));
        assertFalse(body.has("sub"));
        assertFalse(body.has("username"));
        assertFalse(body.has("apiKeys"));
        assertEquals(List.of(Permissions.ROLE_PUBLIC), toStringList(body.get("roles")));
        assertTrue(toStringList(body.get("permissions")).isEmpty());
    }

    @Test
    public void getCurrentUserWithoutAuthReflectsPublicRolePermissions() throws Exception {
        userDatabase.setRolePermissions(Permissions.ROLE_PUBLIC, List.of(Permissions.READ_METADATA));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT)).thenReturn(null);
        when(request.getAttribute(HttpConstants.OIDC.KEY_ROLES))
                .thenReturn(userDatabase.resolveAnonymousRoles());
        when(request.getAttribute(HttpConstants.OIDC.KEY_PERMISSIONS))
                .thenReturn(userDatabase.resolveAnonymousPermissions());
        HandlerTestSupport.bindRequest(usersResource, request);

        Response response = usersResource.getCurrentUser();

        assertEquals(200, response.getStatus());
        JsonNode body = MAPPER.readTree(HandlerTestSupport.entityAsJson(response));
        assertEquals(List.of(Permissions.READ_METADATA), toStringList(body.get("permissions")));
    }

    @Test
    public void getCurrentUserWithAuthReturnsIdentityAndPermissions() throws Exception {
        String sub = "user-42";
        userDatabase.updateUsername(sub, "alice");
        userDatabase.insertAPIKey("dev-key", sub, "hashed-key");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HttpConstants.OIDC.KEY_SUBJECT)).thenReturn(sub);
        when(request.getAttribute(HttpConstants.OIDC.KEY_ROLES))
                .thenReturn(userDatabase.resolveInternalRoles(sub, List.of()));
        when(request.getAttribute(HttpConstants.OIDC.KEY_PERMISSIONS))
                .thenReturn(userDatabase.resolvePermissions(sub, List.of()));
        HandlerTestSupport.bindRequest(usersResource, request);

        Response response = usersResource.getCurrentUser();

        assertEquals(200, response.getStatus());
        JsonNode body = MAPPER.readTree(HandlerTestSupport.entityAsJson(response));
        assertEquals(sub, body.get("sub").asText());
        assertEquals("alice", body.get("username").asText());
        assertEquals(List.of("dev-key"), toStringList(body.get("apiKeys")));
        assertTrue(toStringList(body.get("roles")).contains(Permissions.ROLE_DEFAULT));
        assertTrue(toStringList(body.get("permissions")).contains(Permissions.READ_METADATA));
    }

    private static List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return MAPPER.convertValue(node,
                MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
