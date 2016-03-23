package org.gbif.population.data;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class GridCountMapper implements ResultSetMapper<GridCount> {

  @Override
  public GridCount map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    return new GridCount(r.getInt("x1"), r.getInt("y1"), r.getInt("count"));
  }
}
