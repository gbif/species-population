package org.gbif.population.data;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

@RegisterMapper(PointFeatureMapper.class)
public interface DataDAO {

  @SqlQuery("SELECT json FROM tiles WHERE x=:x AND y=:y AND z=:z AND speciesKey=:speciesKey")
  String lookup(
    @Bind("speciesKey") String speciesKey, @Bind("z") int z,
    @Bind("x") int x, @Bind("y") int y
  );

  // TODO: add the geometery for the tile...
  @SqlQuery("SELECT"
            + "  X(geom) AS longitude,"
            + "  Y(geom) AS latitude,"
            + "  GROUP_CONCAT(CONCAT(year,':', speciesCount, ':',orderCount)) AS features"
            + "  FROM lepidoptera"
            + "  WHERE speciesKey=:speciesKey"
            + "  AND YEAR BETWEEN :minYear AND :maxYear"
            + "  GROUP BY geom"
            + "  HAVING count(year)>=:yearThreshold")
  List<PointFeature> getRecords(
    @Bind("speciesKey") String speciesKey, @Bind("minYear") int minYear,
    @Bind("maxYear") int maxYear, @Bind("yearThreshold") int yearThreshold);

}
