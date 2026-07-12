package org.dbpedia.moss.users;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(UserDatabaseManager.class);

    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private static final String USERDB_COLUMN_USERNAME = "username";
    private static final String USERDB_COLUMN_KEY = "key";

    private final String userDatabasePath;

    public UserDatabaseManager(String userDatabasePath) {
        this.userDatabasePath = userDatabasePath;

        File databaseFile = new File(userDatabasePath);
        File databaseDir = databaseFile.getParentFile();

        if (databaseDir != null && !databaseDir.exists()) {
            boolean dirsCreated = databaseDir.mkdirs();
            if (dirsCreated) {
                logger.debug("Created directories for user database: {}", databaseDir.getAbsolutePath());
            } else {
                logger.error("Failed to create directory for user database: {}", databaseDir.getAbsolutePath());
            }
        }

        try {
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_USER_TABLE));
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_API_KEYS));
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_ROLE_TABLE));
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_PERMISSION_TABLE));
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_ROLE_PERMISSION_TABLE));
            executeUpdate((Connection connection) -> connection.prepareStatement(QUERY_CREATE_USER_ROLE_TABLE));
            seedDefaults();
        } catch (SQLException sqlException) {
            logger.error(sqlException.getMessage());
        }
    }

    private void seedDefaults() throws SQLException {
        for (String permission : Permissions.ALL) {
            executeUpdate((Connection connection) -> {
                PreparedStatement ps = connection.prepareStatement(QUERY_INSERT_PERMISSION);
                ps.setString(1, permission);
                return ps;
            });
        }

        insertRole(Permissions.ROLE_ADMIN, null);
        insertRole(Permissions.ROLE_MAINTAINER, null);
        insertRole(Permissions.ROLE_DEFAULT, null);
        insertRole(Permissions.ROLE_PUBLIC, null);
        migrateLegacyGuestRole();
        migrateLegacyPermissions();

        for (String permission : Permissions.ALL) {
            linkRolePermission(Permissions.ROLE_ADMIN, permission);
        }
        linkRolePermission(Permissions.ROLE_MAINTAINER, Permissions.READ_METADATA);
        linkRolePermission(Permissions.ROLE_MAINTAINER, Permissions.WRITE_ENTRIES);
        linkRolePermission(Permissions.ROLE_MAINTAINER, Permissions.WRITE_MODULES);
        linkRolePermission(Permissions.ROLE_MAINTAINER, Permissions.WRITE_FACETS);
        linkRolePermission(Permissions.ROLE_DEFAULT, Permissions.READ_METADATA);
    }

    private void migrateLegacyPermissions() throws SQLException {
        for (String legacyRead : List.of("read-entries", "read-settings")) {
            executeUpdate((Connection connection) -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO role_permission(role, permission) "
                                + "SELECT role, ? FROM role_permission WHERE permission = ?");
                ps.setString(1, Permissions.READ_METADATA);
                ps.setString(2, legacyRead);
                return ps;
            });
            executeUpdate((Connection connection) -> {
                PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM role_permission WHERE permission = ?");
                ps.setString(1, legacyRead);
                return ps;
            });
        }

        for (String writePermission : List.of(
                Permissions.WRITE_MODULES, Permissions.WRITE_TERMINOLOGIES, Permissions.WRITE_FACETS)) {
            executeUpdate((Connection connection) -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO role_permission(role, permission) "
                                + "SELECT role, ? FROM role_permission WHERE permission = ?");
                ps.setString(1, writePermission);
                ps.setString(2, "write-settings");
                return ps;
            });
        }
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM role_permission WHERE permission = ?");
            ps.setString(1, "write-settings");
            return ps;
        });
    }

    private void migrateLegacyGuestRole() throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement("UPDATE role SET name = ? WHERE name = 'guest'");
            ps.setString(1, Permissions.ROLE_DEFAULT);
            return ps;
        });
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement("UPDATE user_role SET role = ? WHERE role = 'guest'");
            ps.setString(1, Permissions.ROLE_DEFAULT);
            return ps;
        });
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement("UPDATE role_permission SET role = ? WHERE role = 'guest'");
            ps.setString(1, Permissions.ROLE_DEFAULT);
            return ps;
        });
    }

    public List<String> getAPIKeysBySub(String sub) {
        List<String> apiKeys = new ArrayList<>();
        executeSelect(QUERY_SELECT_API_KEYS_BY_SUB, (ResultSet rs) -> {
            while (rs.next()) {
                apiKeys.add(rs.getString(USERDB_COLUMN_KEY));
            }
        }, sub);
        return apiKeys;
    }

    public UserInfo getUserInfoBySub(String sub) {
        final UserInfo[] userInfo = {null};
        executeSelect(QUERY_SELECT_USER_BY_SUB, (ResultSet rs) -> {
            while (rs.next()) {
                UserInfo info = new UserInfo();
                info.setSub(sub);
                info.setUsername(rs.getString(USERDB_COLUMN_USERNAME));
                userInfo[0] = info;
            }
        }, sub);
        return userInfo[0];
    }

    public UserInfo getUserInfoByUsername(String username) {
        final UserInfo[] userInfo = {null};
        executeSelect(QUERY_SELECT_USER_BY_USERNAME, (ResultSet rs) -> {
            while (rs.next()) {
                UserInfo info = new UserInfo();
                info.setSub(rs.getString("sub"));
                info.setUsername(rs.getString(USERDB_COLUMN_USERNAME));
                userInfo[0] = info;
            }
        }, username);
        return userInfo[0];
    }

    public String resolveSub(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        if (getUserInfoBySub(identifier) != null) {
            return identifier;
        }
        UserInfo byUsername = getUserInfoByUsername(identifier);
        return byUsername != null ? byUsername.getSub() : null;
    }

    public List<String> getAPIKeyNamesBySub(String sub) {
        final ArrayList<String> apiKeyNames = new ArrayList<>();
        executeSelect(QUERY_SELECT_API_KEYS_BY_SUB, (ResultSet rs) -> {
            while (rs.next()) {
                apiKeyNames.add(rs.getString("name"));
            }
        }, sub);
        return apiKeyNames;
    }

    public List<UserInfo> listUsers() {
        List<UserInfo> users = new ArrayList<>();
        executeSelect(QUERY_SELECT_ALL_USERS, (ResultSet rs) -> {
            while (rs.next()) {
                UserInfo info = new UserInfo();
                info.setSub(rs.getString("sub"));
                info.setUsername(rs.getString(USERDB_COLUMN_USERNAME));
                users.add(info);
            }
        });
        for (UserInfo user : users) {
            List<String> roles = getUserRoles(user.getSub());
            user.setRoles(roles.toArray(String[]::new));
        }
        return users;
    }

    public void updateUsername(String sub, String username) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_USER);
            preparedStmt.setString(1, sub);
            preparedStmt.setString(2, username);
            return preparedStmt;
        });
    }

    public void ensureUsernameIfUnset(String sub, String username) throws SQLException {
        if (sub == null || sub.isBlank() || username == null || username.isBlank()) {
            return;
        }
        executeUpdate((Connection connection) -> {
            PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_USER_IF_ABSENT);
            preparedStmt.setString(1, sub);
            preparedStmt.setString(2, username);
            return preparedStmt;
        });
    }

    public void insertAPIKey(String name, String sub, String key) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_API_KEY);
            preparedStmt.setString(1, name);
            preparedStmt.setString(2, sub);
            preparedStmt.setString(3, key);
            return preparedStmt;
        });
    }

    public void deleteAPIKey(String sub, String name) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement preparedStmt = connection.prepareStatement(QUERY_DELETE_API_KEY);
            preparedStmt.setString(1, sub);
            preparedStmt.setString(2, name);
            return preparedStmt;
        });
    }

    public Set<String> resolvePermissions(String sub, List<String> tokenRoles) throws SQLException {
        return expandRolesToPermissions(new LinkedHashSet<>(resolveInternalRoles(sub, tokenRoles)));
    }

    public List<String> resolveInternalRoles(String sub, List<String> tokenRoles) {
        Set<String> internalRoles = new LinkedHashSet<>();

        internalRoles.add(Permissions.ROLE_DEFAULT);

        if (tokenRoles != null && !tokenRoles.isEmpty()) {
            internalRoles.addAll(mapTokenRolesToInternal(tokenRoles));
        }

        internalRoles.addAll(getUserRoles(sub));

        return new ArrayList<>(internalRoles);
    }

    public List<String> resolveAnonymousRoles() {
        return List.of(Permissions.ROLE_PUBLIC);
    }

    public Set<String> resolveAnonymousPermissions() {
        return expandRolesToPermissions(new LinkedHashSet<>(resolveAnonymousRoles()));
    }

    public void ensureAdminUser(String sub) throws SQLException {
        if (sub == null || sub.isBlank()) {
            return;
        }
        assignUserRole(sub, Permissions.ROLE_ADMIN);
    }

    public List<String> getUserRoles(String sub) {
        List<String> roles = new ArrayList<>();
        executeSelect(QUERY_SELECT_USER_ROLES, (ResultSet rs) -> {
            while (rs.next()) {
                roles.add(rs.getString("role"));
            }
        }, sub);
        return roles;
    }

    public void assignUserRole(String sub, String role) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_INSERT_USER_ROLE);
            ps.setString(1, sub);
            ps.setString(2, role);
            return ps;
        });
    }

    public void revokeUserRole(String sub, String role) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_USER_ROLE);
            ps.setString(1, sub);
            ps.setString(2, role);
            return ps;
        });
    }

    public void setUserRoles(String sub, List<String> roles) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_ALL_USER_ROLES);
            ps.setString(1, sub);
            return ps;
        });
        if (roles != null) {
            for (String role : roles) {
                assignUserRole(sub, role);
            }
        }
    }

    public List<MossRole> listRoles() {
        List<MossRole> roles = new ArrayList<>();
        executeSelect(QUERY_SELECT_ALL_ROLES, (ResultSet rs) -> {
            while (rs.next()) {
                MossRole role = new MossRole();
                role.setName(rs.getString("name"));
                role.setTokenRole(rs.getString("token_role"));
                roles.add(role);
            }
        });
        for (MossRole role : roles) {
            role.setPermissions(getRolePermissions(role.getName()));
        }
        return roles;
    }

    public MossRole getRole(String name) {
        final MossRole[] result = {null};
        executeSelect(QUERY_SELECT_ROLE, (ResultSet rs) -> {
            if (rs.next()) {
                MossRole role = new MossRole();
                role.setName(rs.getString("name"));
                role.setTokenRole(rs.getString("token_role"));
                result[0] = role;
            }
        }, name);
        if (result[0] != null) {
            result[0].setPermissions(getRolePermissions(name));
        }
        return result[0];
    }

    public void createRole(String name, String tokenRole) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_INSERT_ROLE);
            ps.setString(1, name);
            ps.setString(2, tokenRole);
            return ps;
        });
    }

    public void updateRole(String name, String tokenRole) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_UPDATE_ROLE);
            ps.setString(1, tokenRole);
            ps.setString(2, name);
            return ps;
        });
    }

    public void deleteRole(String name) throws SQLException {
        if (isAdminRole(name)) {
            throw new IllegalArgumentException("The admin role cannot be deleted.");
        }
        if (isDefaultRole(name)) {
            throw new IllegalArgumentException("The default role cannot be deleted.");
        }
        if (isPublicRole(name)) {
            throw new IllegalArgumentException("The public role cannot be deleted.");
        }
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_ROLE_PERMISSIONS_FOR_ROLE);
            ps.setString(1, name);
            return ps;
        });
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_USER_ROLES_FOR_ROLE);
            ps.setString(1, name);
            return ps;
        });
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_ROLE);
            ps.setString(1, name);
            return ps;
        });
    }

    public List<String> getRolePermissions(String role) {
        if (isAdminRole(role)) {
            return Arrays.asList(Permissions.ALL);
        }
        List<String> permissions = new ArrayList<>();
        executeSelect(QUERY_SELECT_ROLE_PERMISSIONS, (ResultSet rs) -> {
            while (rs.next()) {
                permissions.add(rs.getString("permission"));
            }
        }, role);
        return permissions;
    }

    public void setRolePermissions(String role, List<String> permissions) throws SQLException {
        if (isAdminRole(role)) {
            throw new IllegalArgumentException("The admin role permissions cannot be modified.");
        }
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_DELETE_ROLE_PERMISSIONS_FOR_ROLE);
            ps.setString(1, role);
            return ps;
        });
        if (permissions != null) {
            for (String permission : permissions) {
                linkRolePermission(role, permission);
            }
        }
    }

    private void insertRole(String name, String tokenRole) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_INSERT_ROLE);
            ps.setString(1, name);
            ps.setString(2, tokenRole);
            return ps;
        });
    }

    private void linkRolePermission(String role, String permission) throws SQLException {
        executeUpdate((Connection connection) -> {
            PreparedStatement ps = connection.prepareStatement(QUERY_INSERT_ROLE_PERMISSION);
            ps.setString(1, role);
            ps.setString(2, permission);
            return ps;
        });
    }

    private List<String> mapTokenRolesToInternal(List<String> tokenRoles) {
        if (tokenRoles.isEmpty()) {
            return List.of();
        }
        List<String> mapped = new ArrayList<>();
        String placeholders = String.join(",", tokenRoles.stream().map(r -> "?").toList());
        String query = "SELECT name FROM role WHERE token_role IN (" + placeholders + ")";
        executeSelect(query, (ResultSet rs) -> {
            while (rs.next()) {
                mapped.add(rs.getString("name"));
            }
        }, tokenRoles.toArray());
        return mapped;
    }

    private Set<String> expandRolesToPermissions(Set<String> roles) {
        Set<String> permissions = new HashSet<>();
        if (roles.contains(Permissions.ROLE_ADMIN)) {
            permissions.addAll(Arrays.asList(Permissions.ALL));
        }
        if (roles.isEmpty()) {
            return permissions;
        }
        String placeholders = String.join(",", roles.stream().map(r -> "?").toList());
        String query = "SELECT DISTINCT permission FROM role_permission WHERE role IN (" + placeholders + ")";
        executeSelect(query, (ResultSet rs) -> {
            while (rs.next()) {
                permissions.add(rs.getString("permission"));
            }
        }, roles.toArray());
        return permissions;
    }

    private static boolean isAdminRole(String role) {
        return Permissions.ROLE_ADMIN.equals(role);
    }

    private static boolean isDefaultRole(String role) {
        return Permissions.ROLE_DEFAULT.equals(role);
    }

    private static boolean isPublicRole(String role) {
        return Permissions.ROLE_PUBLIC.equals(role);
    }

    public void executeUpdate(IStatementProvider statementProvider) throws SQLException {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(getDatabaseURL());
            PreparedStatement statement = statementProvider.createStatement(conn);
            int updatedRows = statement.executeUpdate();
            logger.debug("Executed prepared statement: {} --- inserted {} rows", statement, updatedRows);
        } catch (SQLException sqlException) {
            close(conn);
            throw sqlException;
        } finally {
            close(conn);
        }
    }

    public void executeSelect(String query, IResultSetCallback callback, Object... params) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(getDatabaseURL());
            PreparedStatement statement = conn.prepareStatement(query);
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                callback.process(rs);
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } finally {
            close(conn);
        }
    }

    private void close(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException sqlException) {
            logger.error(sqlException.getMessage());
        }
    }

    private String getDatabaseURL() {
        return JDBC_PREFIX + userDatabasePath;
    }

    private static final String QUERY_CREATE_USER_TABLE = """
        CREATE TABLE IF NOT EXISTS user(
            sub text PRIMARY KEY,
            username text NOT NULL
        );""";

    private static final String QUERY_CREATE_API_KEYS = """
        CREATE TABLE IF NOT EXISTS api(
            name TEXT NOT NULL,
            sub TEXT NOT NULL,
            key TEXT NOT NULL,
            PRIMARY KEY (name, sub)
        );""";

    private static final String QUERY_CREATE_ROLE_TABLE = """
        CREATE TABLE IF NOT EXISTS role(
            name TEXT PRIMARY KEY,
            token_role TEXT UNIQUE
        );""";

    private static final String QUERY_CREATE_PERMISSION_TABLE = """
        CREATE TABLE IF NOT EXISTS permission(
            name TEXT PRIMARY KEY
        );""";

    private static final String QUERY_CREATE_ROLE_PERMISSION_TABLE = """
        CREATE TABLE IF NOT EXISTS role_permission(
            role TEXT NOT NULL,
            permission TEXT NOT NULL,
            PRIMARY KEY (role, permission)
        );""";

    private static final String QUERY_CREATE_USER_ROLE_TABLE = """
        CREATE TABLE IF NOT EXISTS user_role(
            sub TEXT NOT NULL,
            role TEXT NOT NULL,
            PRIMARY KEY (sub, role)
        );""";

    private static final String QUERY_DELETE_API_KEY = """
        DELETE FROM api WHERE sub = ? AND name = ?;""";

    private static final String QUERY_INSERT_API_KEY = """
        INSERT INTO api(name, sub, key) VALUES(?, ?, ?);""";

    private static final String QUERY_INSERT_USER = """
        INSERT OR REPLACE INTO user (sub, username) VALUES (?, ?);""";

    private static final String QUERY_INSERT_USER_IF_ABSENT = """
        INSERT OR IGNORE INTO user (sub, username) VALUES (?, ?);""";

    private static final String QUERY_SELECT_API_KEYS_BY_SUB = """
        SELECT key, name FROM api WHERE sub = ?;""";

    private static final String QUERY_SELECT_USER_BY_SUB = """
        SELECT sub, username FROM user WHERE sub = ?;""";

    private static final String QUERY_SELECT_USER_BY_USERNAME = """
        SELECT sub, username FROM user WHERE username = ?;""";

    private static final String QUERY_SELECT_ALL_USERS = """
        SELECT sub, username FROM user ORDER BY username;""";

    private static final String QUERY_INSERT_PERMISSION = """
        INSERT OR IGNORE INTO permission(name) VALUES(?);""";

    private static final String QUERY_INSERT_ROLE = """
        INSERT OR IGNORE INTO role(name, token_role) VALUES(?, ?);""";

    private static final String QUERY_UPDATE_ROLE = """
        UPDATE role SET token_role = ? WHERE name = ?;""";

    private static final String QUERY_INSERT_ROLE_PERMISSION = """
        INSERT OR IGNORE INTO role_permission(role, permission) VALUES(?, ?);""";

    private static final String QUERY_INSERT_USER_ROLE = """
        INSERT OR IGNORE INTO user_role(sub, role) VALUES(?, ?);""";

    private static final String QUERY_DELETE_USER_ROLE = """
        DELETE FROM user_role WHERE sub = ? AND role = ?;""";

    private static final String QUERY_DELETE_ALL_USER_ROLES = """
        DELETE FROM user_role WHERE sub = ?;""";

    private static final String QUERY_SELECT_USER_ROLES = """
        SELECT role FROM user_role WHERE sub = ?;""";

    private static final String QUERY_SELECT_ALL_ROLES = """
        SELECT name, token_role FROM role ORDER BY name;""";

    private static final String QUERY_SELECT_ROLE = """
        SELECT name, token_role FROM role WHERE name = ?;""";

    private static final String QUERY_DELETE_ROLE = """
        DELETE FROM role WHERE name = ?;""";

    private static final String QUERY_DELETE_ROLE_PERMISSIONS_FOR_ROLE = """
        DELETE FROM role_permission WHERE role = ?;""";

    private static final String QUERY_DELETE_USER_ROLES_FOR_ROLE = """
        DELETE FROM user_role WHERE role = ?;""";

    private static final String QUERY_SELECT_ROLE_PERMISSIONS = """
        SELECT permission FROM role_permission WHERE role = ? ORDER BY permission;""";
}
