package org.dbpedia.moss.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.sql.PreparedStatement;

import org.mindrot.jbcrypt.BCrypt;

public class UserDatabaseManager {

    private static String prefix = "jdbc:sqlite:";
    private String userDatabasePath = "/home/john/Documents/workspace/whk/moss-jetty/devenv/sqlite/auth.db";

    public UserDatabaseManager(String userDatabasePath) {
        this.userDatabasePath = userDatabasePath;

        createUserTable();
        createAPITable();
    }

    private String GetDatabaseURL() {
        return prefix + userDatabasePath;
    }

    private static String hashPassword(String plainTextPassword) {
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
    }

    private void checkPass(String plainTextPassword, String hashedPassword) {
        if (BCrypt.checkpw(plainTextPassword, hashedPassword)) {
            System.out.println("Matching password.");
            return;
        }
        System.out.println("Password does not match");
    }

    public static void close(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        }
    }

    public static void query(Connection connection, String sql, String column) {
        ResultSet resultSet;
        try {
            Statement stmt = connection.createStatement();
            resultSet = stmt.executeQuery(sql);

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Iterate through the result set
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    String columnValue = resultSet.getString(i);
                    System.out.println(String.format("%s: %s", columnName, columnValue));
                }
                System.out.println("---"); // Separator between rows
            }
        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }
    }

    public static HashMap<String, String> queryResult(Connection connection, String sql, String column) {
        ResultSet resultSet;
        HashMap<String, String> rows = new HashMap<>();
        try {
            resultSet = preparedStmt(connection, sql, column);

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Iterate through the result set
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    String columnValue = resultSet.getString(i);
                    rows.put(columnName, columnValue);
                }
            }
        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }

        return rows;
    }

    public static boolean stmt(Connection connection, String sql) {
        boolean successful = false;
        try {
            Statement stmt = connection.createStatement();
            successful = stmt.execute(sql);

        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }
        return successful;
    }

    public static void preparedStmt(Connection connection, String sql, String sub, String username) {
        try {
            PreparedStatement preparedStmt = connection.prepareStatement(sql);
            preparedStmt.setString(1, sub);
            preparedStmt.setString(2, username);
            int updatedRows = preparedStmt.executeUpdate();
            System.out.println(String.format("Inserted rows %s", updatedRows));
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void preparedStmt(Connection connection, String sql, String key, String sub, String name) {
        try {
            PreparedStatement preparedStmt = connection.prepareStatement(sql);
            preparedStmt.setString(1, key);
            preparedStmt.setString(2, sub);
            preparedStmt.setString(3, name);
            int updatedRows = preparedStmt.executeUpdate();
            System.out.println(String.format("Prepared statement:\n%s", preparedStmt.toString()));
            System.out.println(String.format("Inserted rows %s", updatedRows));
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static ResultSet preparedStmt(Connection connection, String sql, String column) {
        ResultSet resultSet;
        try {
            PreparedStatement preparedStmt = connection.prepareStatement(sql);
            preparedStmt.setString(1, column);
            // int updatedRows = preparedStmt.executeUpdate();
            resultSet = preparedStmt.executeQuery();
            System.out.println(String.format("Prepared statement:\n%s", preparedStmt.toString()));
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return null;
        }
        return resultSet;
    }

    private void createTable(String sql) {
        Connection conn = null;
        boolean successful;

        try {
            System.out.println(GetDatabaseURL());
            conn = DriverManager.getConnection(GetDatabaseURL());
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            stmt(conn, sql);
            successful = true;

        } catch (SQLException sqlException) {
            successful = false;
            System.out.println(sqlException.getMessage());
        } finally {
            UserDatabaseManager.close(conn);
        }
        System.out.println(String.format("Successful: %s", successful));
    }

    public void createQuery(String sql) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(GetDatabaseURL());
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            query(conn, sql, "name");

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            UserDatabaseManager.close(conn);
        }
    }

    public HashMap<String, String> fetchQuery(String sql, String name) {
        Connection conn = null;
        HashMap<String, String> result = new HashMap<>();

        try {
            conn = DriverManager.getConnection(GetDatabaseURL());
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            result = queryResult(conn, sql, name);

            System.out.println("Results");

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            UserDatabaseManager.close(conn);
        }
        return result;
    }

    public void insertQuery(String sql, String sub, String username) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(GetDatabaseURL());
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            preparedStmt(conn, sql, sub, username);

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            UserDatabaseManager.close(conn);
        }
    }

    public void insertQuery(String sql, String key, String sub, String name) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(GetDatabaseURL());
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            preparedStmt(conn, sql, key, sub, name);

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            UserDatabaseManager.close(conn);
        }
    }

    public void getUsers() {
        String sql = """
        SELECT sub, name FROM user
        """;
        createQuery(sql);
    }

    private void createUserTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS user(
            sub text PRIMARY KEY,
            name text NOT NULL
        );
        """;
        createTable(sql);
    }

    private void createAPITable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS api(
            key text PRIMARY KEY,
            sub text NOT NULL,
            name text NOT NULL
        );""";
        createTable(sql);
    }

    public void insertUser(String sub, String username) {
        String sql = "INSERT INTO user(sub,name) VALUES(?, ?)";
        insertQuery(sql, sub, username);
    }

    public void insertKey(String key, String sub, String name) {
        String sql = "INSERT INTO api(key, sub, name) VALUES(?, ?, ?)";
        String saltedHashedKey = UserDatabaseManager.hashPassword(key);
        String saltedHashSub = UserDatabaseManager.hashPassword(sub);
        insertQuery(sql, saltedHashedKey, saltedHashSub, name);
    }

    public void getKey(String name) {
        String sql = """
        SELECT key, sub, name FROM api
        WHERE name == ?
        """;
        HashMap<String, String> result = fetchQuery(sql, name);

        for(Map.Entry<String, String> entry: result.entrySet()) {
            System.out.println("key:" + entry.getKey());
            System.out.println("value:" + entry.getValue());
        }
    }
}
