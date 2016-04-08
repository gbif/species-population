package org.gbif.population.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class YearFeatureMapper implements ResultSetMapper<YearFeature> {
  @Override
  public YearFeature map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    return new YearFeature(r.getInt("year"), r.getInt("speciesCount"), r.getInt("orderCount"));
  }
}
