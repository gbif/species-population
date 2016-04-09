package org.gbif.population.data;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

@RegisterMapper({PointFeatureMapper.class, ScientificNameMapper.class})
public interface DataDAO {

  /**
   * NOTE: we don't limit by the geometry so all tiles get all data (which caches well)
   * This could (should) be revised for e.g. Aves
   */
  @SqlQuery("SELECT"
            + "  X(geom) AS longitude,"
            + "  Y(geom) AS latitude,"
            + "  yearCounts"
            + "  FROM lepidoptera"
            + "  WHERE speciesKey=:speciesKey")
  List<PointFeature> getSpeciesCounts(@Bind("speciesKey") int speciesKey);

  @SqlQuery("SELECT"
            + "  X(geom) AS longitude,"
            + "  Y(geom) AS latitude,"
            + "  yearCounts"
            + "  FROM lepidoptera_group")
  List<PointFeature> getGroupCounts();

  @SqlQuery("SELECT speciesKey, scientificName FROM lepidoptera_names "
            + "WHERE scientificName like :prefix "
            + "ORDER BY scientificName LIMIT 25")
  List<ScientificName> autocomplete(@Bind("prefix") String prefix);
}
