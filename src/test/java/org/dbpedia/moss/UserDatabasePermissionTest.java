package org.dbpedia.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.dbpedia.moss.db.MossRole;
import org.dbpedia.moss.db.Permissions;
import org.dbpedia.moss.db.UserDatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserDatabasePermissionTest {

    private File databaseFile;
    private UserDatabaseManager userDatabase;

    @BeforeEach
    public void setUp() throws Exception {
        databaseFile = Files.createTempFile("moss-perm-test", ".db").toFile();
        databaseFile.deleteOnExit();
        userDatabase = new UserDatabaseManager(databaseFile.getAbsolutePath());
    }

    @AfterEach
    public void tearDown() {
        if (databaseFile != null && databaseFile.exists()) {
            databaseFile.delete();
        }
    }

    @Test
    public void guestRoleIsAppliedWhenUserHasNoRoles() throws Exception {
        Set<String> permissions = userDatabase.resolvePermissions("user-1", List.of());
        assertTrue(permissions.contains(Permissions.READ_ENTRIES));
        assertTrue(permissions.contains(Permissions.READ_SETTINGS));
        assertEquals(2, permissions.size());
    }

    @Test
    public void adminBootstrapAssignsAllPermissions() throws Exception {
        userDatabase.ensureAdminUser("admin-user");
        Set<String> permissions = userDatabase.resolvePermissions("admin-user", List.of());
        for (String permission : Permissions.ALL) {
            assertTrue(permissions.contains(permission));
        }
    }

    @Test
    public void tokenRoleMappingIsApplied() throws Exception {
        userDatabase.createRole("idp-maintainer", "maintainer-token");
        userDatabase.setRolePermissions("idp-maintainer", List.of(Permissions.WRITE_ENTRIES));

        List<String> roles = userDatabase.resolveInternalRoles("user-2", List.of("maintainer-token"));
        assertTrue(roles.contains("idp-maintainer"));

        Set<String> permissions = userDatabase.resolvePermissions("user-2", List.of("maintainer-token"));
        assertTrue(permissions.contains(Permissions.WRITE_ENTRIES));
    }

    @Test
    public void updateRoleChangesTokenRoleMapping() throws Exception {
        userDatabase.createRole("custom-admin", "old-token");
        userDatabase.updateRole("custom-admin", "moss_admin_very_long");

        List<String> roles = userDatabase.resolveInternalRoles("user-7", List.of("moss_admin_very_long"));
        assertTrue(roles.contains("custom-admin"));
    }

    @Test
    public void ensureUsernameIfUnsetSetsPreferredUsernameOnFirstLogin() throws Exception {
        userDatabase.ensureUsernameIfUnset("user-4", "janfo");
        assertEquals("janfo", userDatabase.getUserInfoBySub("user-4").getUsername());
    }

    @Test
    public void ensureUsernameIfUnsetDoesNotOverwriteExistingUsername() throws Exception {
        userDatabase.updateUsername("user-5", "custom-name");
        userDatabase.ensureUsernameIfUnset("user-5", "janfo");
        assertEquals("custom-name", userDatabase.getUserInfoBySub("user-5").getUsername());
    }

    @Test
    public void ensureUsernameIfUnsetIgnoresBlankValues() throws Exception {
        userDatabase.ensureUsernameIfUnset("user-6", "  ");
        assertNull(userDatabase.getUserInfoBySub("user-6"));
    }

    @Test
    public void resolveSubFindsUserByUsername() throws Exception {
        userDatabase.updateUsername("user-8", "alice");
        assertEquals("user-8", userDatabase.resolveSub("alice"));
        assertEquals("user-8", userDatabase.resolveSub("user-8"));
        assertNull(userDatabase.resolveSub("unknown"));
    }

    @Test
    public void adminRoleIncludesAllPermissions() {
        assertEquals(Arrays.asList(Permissions.ALL), userDatabase.getRolePermissions(Permissions.ROLE_ADMIN));
    }

    @Test
    public void adminRoleCannotBeDeleted() {
        assertThrows(IllegalArgumentException.class, () -> userDatabase.deleteRole(Permissions.ROLE_ADMIN));
    }

    @Test
    public void adminTokenRoleCanBeUpdated() throws Exception {
        userDatabase.updateRole(Permissions.ROLE_ADMIN, "moss-admin-token");

        MossRole admin = userDatabase.getRole(Permissions.ROLE_ADMIN);
        assertEquals("moss-admin-token", admin.getTokenRole());

        List<String> roles = userDatabase.resolveInternalRoles("oidc-admin", List.of("moss-admin-token"));
        assertTrue(roles.contains(Permissions.ROLE_ADMIN));
    }

    @Test
    public void adminRolePermissionsCannotBeModified() {
        assertThrows(IllegalArgumentException.class,
                () -> userDatabase.setRolePermissions(Permissions.ROLE_ADMIN, List.of(Permissions.READ_ENTRIES)));
        assertEquals(Arrays.asList(Permissions.ALL), userDatabase.getRolePermissions(Permissions.ROLE_ADMIN));
    }

    @Test
    public void listUsersReturnsAssignedRoles() throws Exception {
        userDatabase.updateUsername("user-9", "bob");
        userDatabase.assignUserRole("user-9", Permissions.ROLE_MAINTAINER);

        var users = userDatabase.listUsers();
        var bob = users.stream().filter(u -> "bob".equals(u.getUsername())).findFirst().orElseThrow();
        assertTrue(List.of(bob.getRoles()).contains(Permissions.ROLE_MAINTAINER));
    }

    @Test
    public void userRoleOverridesDefaultGuestRole() throws Exception {
        userDatabase.assignUserRole("user-3", Permissions.ROLE_MAINTAINER);
        Set<String> permissions = userDatabase.resolvePermissions("user-3", List.of());
        assertTrue(permissions.contains(Permissions.WRITE_ENTRIES));
        assertTrue(permissions.contains(Permissions.READ_SETTINGS));
    }
}
