package org.dbpedia.moss.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface IStatementProvider {
    PreparedStatement createStatement(Connection connection) throws SQLException;
}