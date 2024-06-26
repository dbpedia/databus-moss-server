package org.dbpedia.moss.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import org.mindrot.jbcrypt.BCrypt;

public class UserDatabaseManager {

    private static String prefix = "jdbc:sqlite:";
    private String userDatabasePath = "/home/john/Documents/workspace/whk/moss-jetty/devenv/sqlite/auth.db";

    public UserDatabaseManager(String userDatabasePath) {
        this.userDatabasePath = userDatabasePath;

        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                return connection.prepareStatement(QUERY_CREATE_USER_TABLE);
            }
        });

        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                return connection.prepareStatement(QUERY_CREATE_API_KEYS);
            }
        });
    }

    /**
     * Get all API keys of a user (identified by sub)
     * @param sub
     * @return
     * @throws IOException
     */
    public List<String> getAPIKeysBySub(String sub) {
        List<String> apiKeys = new ArrayList<>();
    
        executeSelect(QUERY_SELECT_API_KEYS_BY_SUB, new IResultSetCallback() {
            @Override
            public void process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    apiKeys.add(rs.getString("key"));
                }
            }
        }, sub);
    
        return apiKeys;
    }

    public UserInfo getUserInfoBySub(String sub) {
        final UserInfo[] userInfo = { null };

        executeSelect(QUERY_SELECT_USER_BY_SUB, new IResultSetCallback() {
            @Override
            public void process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    UserInfo info = new UserInfo();
                    info.setSub(sub);
                    info.setUsername(rs.getString("username"));
                    userInfo[0] = info;
                }
            }
        }, sub);

        return userInfo[0];
    }

   
    public void insertUser(String sub, String username) throws IOException {
        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_USER);
                preparedStmt.setString(1, sub);
                preparedStmt.setString(2, username);
                return preparedStmt;
            }
        });
    }

    public void insertAPIKey(String key, String sub, String name) throws IOException {
        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                String saltedHashedKey = BCrypt.hashpw(key, BCrypt.gensalt());
              
                PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_API_KEY);
                preparedStmt.setString(1, saltedHashedKey);
                preparedStmt.setString(2, sub);
                preparedStmt.setString(3, name);
                return preparedStmt;
            }
        });
    }

    /**
     * Update query helper function
     */
    public void executeUpdate(IStatementProvider statementProvider) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(getDatabaseURL());
            
            PreparedStatement statement = statementProvider.createStatement(conn);
            int updatedRows = statement.executeUpdate();
            System.out.println(String.format("Prepared statement:\n%s", statement.toString()));
            System.out.println(String.format("Inserted rows %s", updatedRows));

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            close(conn);
        }
    }

    /**
     * Select query helper function with result set callback
     * @param query
     * @param callback
     * @param params
     */
    public void executeSelect(String query, IResultSetCallback callback, Object... params) {

        Connection conn = null;

        try {
            conn = DriverManager.getConnection(getDatabaseURL());
            PreparedStatement statement = conn.prepareStatement(query);

            // Set the parameters for the prepared statement
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }

            // Execute the query
            try (ResultSet rs = statement.executeQuery()) {
                // Process the result set using the callback
                callback.process(rs);
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            close(conn);
        }
    }

    /**
     * Close the database connection
     * @param connection
     */
    private void close(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        }
    }

    /**
     * the database URL specified in the constructor with SQLite prefix
     * @return
     */
    private String getDatabaseURL() {
        return prefix + userDatabasePath;
    }

    private static String QUERY_CREATE_USER_TABLE = """
        CREATE TABLE IF NOT EXISTS user(
            sub text PRIMARY KEY,
            username text NOT NULL
        );""";;

    private static String QUERY_CREATE_API_KEYS = """
        CREATE TABLE IF NOT EXISTS api(
            key text PRIMARY KEY,
            sub text NOT NULL,
            name text NOT NULL
        );""";

    private static String QUERY_INSERT_API_KEY = """
        INSERT INTO api(key, sub, name) VALUES(?, ?, ?);
            """;

    private static String QUERY_INSERT_USER = """
        INSERT INTO user(sub, username) VALUES(?, ?);
            """;

    private static String QUERY_SELECT_API_KEYS_BY_SUB = """
        SELECT key FROM api WHERE sub = ?;
            """;

    private static String QUERY_SELECT_USER_BY_SUB = """
        SELECT sub, username FROM user WHERE sub = ?;
            """;

}
