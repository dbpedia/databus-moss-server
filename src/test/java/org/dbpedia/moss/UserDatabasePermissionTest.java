package org.dbpedia.moss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

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
    public void userRoleOverridesDefaultGuestRole() throws Exception {
        userDatabase.assignUserRole("user-3", Permissions.ROLE_MAINTAINER);
        Set<String> permissions = userDatabase.resolvePermissions("user-3", List.of());
        assertTrue(permissions.contains(Permissions.WRITE_ENTRIES));
        assertTrue(permissions.contains(Permissions.READ_SETTINGS));
    }
}
