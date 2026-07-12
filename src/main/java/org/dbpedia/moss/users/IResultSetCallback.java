package org.dbpedia.moss.users;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface IResultSetCallback {
    void process(ResultSet resultSet) throws SQLException;
}