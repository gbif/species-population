package org.gbif.population.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class PointFeatureMapper implements ResultSetMapper<PointFeature> {
  private final Pattern COMMA = Pattern.compile(",");
  private final Pattern COLON = Pattern.compile(":");

  @Override
  public PointFeature map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    PointFeature f = new PointFeature(r.getDouble("latitude"), r.getDouble("longitude"));
    String features = r.getString("features");
    String[] atoms = COMMA.split(features);
    for (String feature : atoms) {
      String[] s = COLON.split(feature);
      f.getSpeciesCounts().put(Integer.valueOf(s[0]), Integer.valueOf(s[1])); // year, speciesCount
      f.getGroupCounts().put(Integer.valueOf(s[0]), Integer.valueOf(s[2])); // year, groupCount
    }
    return f;
  }
}
