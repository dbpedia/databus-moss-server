package org.dbpedia.moss.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface IStatementProvider {
    PreparedStatement createStatement(Connection connection) throws SQLException;
}