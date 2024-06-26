package org.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.PreparedStatement;


public class SqliteConnector {

    static String prefix = "jdbc:sqlite:";
    static String URL = "jdbc:sqlite:/home/john/Documents/workspace/whk/moss-jetty/devenv/sqlite/auth.db";

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

    private static void createTable(String sql) {
        Connection conn = null;
        boolean successful;

        try {
            System.out.println(SqliteConnector.URL);
            conn = DriverManager.getConnection(SqliteConnector.URL);
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            stmt(conn, sql);
            successful = true;

        } catch (SQLException sqlException) {
            successful = false;
            System.out.println(sqlException.getMessage());
        } finally {
            SqliteConnector.close(conn);
        }
        System.out.println(String.format("Successful: %s", successful));
    }

    public static void createQuery(String sql) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(SqliteConnector.URL);
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            query(conn, sql, "name");

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            SqliteConnector.close(conn);
        }
    }

    public static void insertQuery(String sql, String sub, String username) {
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(SqliteConnector.URL);
            System.out.println("Connection to SQLite has been established.");
            System.out.println(String.format("Executing Statement:\n%s", sql));

            preparedStmt(conn, sql, sub, username);

        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
        } finally {
            SqliteConnector.close(conn);
        }
    }

    public static void getUsers() {
        String sql = """
        SELECT sub, name FROM user
        """;
        createQuery(sql);
    }

    public static void createUserTable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS user(
            sub text PRIMARY KEY,
            name text NOT NULL
        );
        """;
        createTable(sql);
    }

    public static void createAPITable() {
        String sql = """
        CREATE TABLE IF NOT EXISTS api
            key text PRIMARY KEY,
            sub text NOT NULL
        """;
        createTable(sql);
    }

    public static void insertUser(String sub, String username) {
        String sql = "INSERT INTO user(sub,name) VALUES(?, ?)";
        insertQuery(sql, sub, username);
    }
}
