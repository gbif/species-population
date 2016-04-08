package org.gbif.population.data;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class ScientificNameMapper implements ResultSetMapper<ScientificName> {
  @Override
  public ScientificName map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    return new ScientificName(r.getInt("speciesKey"), r.getString("scientificName"));
  }
}
