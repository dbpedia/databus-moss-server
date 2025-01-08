package org.dbpedia.moss.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;

public class UserDatabaseManager {

	final static Logger logger = LoggerFactory.getLogger(UserDatabaseManager.class);

    private static String prefix = "jdbc:sqlite:";

    private static final String USERDB_COLUMN_USERNAME = "username";

    private static final String USERDB_COLUMN_KEY = "key";

    private String userDatabasePath = "";

    public UserDatabaseManager(String userDatabasePath) {
        this.userDatabasePath = userDatabasePath;

         File databaseFile = new File(userDatabasePath);
        File databaseDir = databaseFile.getParentFile(); // Get the directory of the database file

        if (databaseDir != null && !databaseDir.exists()) {
            boolean dirsCreated = databaseDir.mkdirs(); // Create the directory if it doesn't exist

            if (dirsCreated) {
                logger.debug("Created directories for user database: {}", databaseDir.getAbsolutePath());
            } else {
                logger.error("Failed to create directory for user database: {}", databaseDir.getAbsolutePath());
            }
        }

        
        try {
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
        } catch(SQLException sqlException) {
            logger.error(sqlException.getMessage());
        }
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
                    apiKeys.add(rs.getString(USERDB_COLUMN_KEY));
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
                    info.setUsername(rs.getString(USERDB_COLUMN_USERNAME));
                    userInfo[0] = info;
                }
            }
        }, sub);

        return userInfo[0];
    }

    public List<String> getAPIKeyNamesBySub(String sub) {

        final ArrayList<String> apiKeyNames = new ArrayList<>();

        executeSelect(QUERY_SELECT_API_KEYS_BY_SUB, new IResultSetCallback() {
            @Override
            public void process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    apiKeyNames.add(rs.getString("name"));
                }
            }
        }, sub);

        return apiKeyNames;
    }



    public void updateUsername(String sub, String username) throws SQLException {
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

    public void insertAPIKey(String name, String sub, String key) throws SQLException {
        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                PreparedStatement preparedStmt = connection.prepareStatement(QUERY_INSERT_API_KEY);
                preparedStmt.setString(1, name);
                preparedStmt.setString(2, sub);
                preparedStmt.setString(3, key);
                return preparedStmt;
            }
        });
    }

    public void deleteAPIKey( String sub, String name) throws SQLException {
        executeUpdate(new IStatementProvider() {
            @Override
            public PreparedStatement createStatement(Connection connection) throws SQLException {
                PreparedStatement preparedStmt = connection.prepareStatement(QUERY_DELETE_API_KEY);
                preparedStmt.setString(1, sub);
                preparedStmt.setString(2, name);
                return preparedStmt;
            }
        });
    }

    /**
     * Update query helper function
     */
    public void executeUpdate(IStatementProvider statementProvider) throws SQLException {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(getDatabaseURL());

            PreparedStatement statement = statementProvider.createStatement(conn);
            int updatedRows = statement.executeUpdate();

            logger.debug("Executed prepared statement: {} --- inserted {} rows", statement.toString(), updatedRows);

        } catch (SQLException sqlException) {
            close(conn);
            throw sqlException;
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
            logger.error(e.getMessage());
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
            logger.error(sqlException.getMessage());
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
            name text PRIMARY KEY,
            sub text NOT NULL,
            key text NOT NULL
        );""";

    private static String QUERY_DELETE_API_KEY = """
        DELETE FROM api WHERE  sub = ? AND name = ?;""";

    private static String QUERY_INSERT_API_KEY = """
        INSERT INTO api(name, sub, key) VALUES(?, ?, ?);
            """;

    private static String QUERY_INSERT_USER = """
        INSERT OR REPLACE  INTO user (sub, username) VALUES (?, ?)
            """;

    private static String QUERY_SELECT_API_KEYS_BY_SUB = """
        SELECT key, name FROM api WHERE sub = ?;
            """;

    private static String QUERY_SELECT_USER_BY_SUB = """
        SELECT sub, username FROM user WHERE sub = ?;
            """;

}
