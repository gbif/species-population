package org.gbif.population.data;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class PointFeatureMapper implements ResultSetMapper<PointFeature> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public PointFeature map(int i, ResultSet r, StatementContext statementContext) throws SQLException {
    try {
      Map<String, Integer> years = MAPPER.readValue(r.getString("yearCounts"), Map.class);
      return new PointFeature(r.getDouble("latitude"), r.getDouble("longitude"), years);
    } catch (IOException e) {
      throw new SQLException("Data expected to be in valid JSON format", e);
    }
  }

}
