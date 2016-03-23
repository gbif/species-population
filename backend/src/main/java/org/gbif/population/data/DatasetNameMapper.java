package org.gbif.population.data;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class DatasetNameMapper implements ResultSetMapper<DatasetName> {

  @Override
  public DatasetName map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    return new DatasetName(r.getString("dataset_key"), r.getString("name"));
  }
}
