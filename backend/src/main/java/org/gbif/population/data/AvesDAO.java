package org.gbif.population.data;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapperFactory;

@RegisterMapper({PointFeatureMapper.class, ScientificNameMapper.class})
public interface AvesDAO extends DataDAO {

  /**
   * NOTE: we don't limit by the geometry so all tiles get all data (which caches well)
   * This could (should) be revised for e.g. Aves
   */
  @SqlQuery("SELECT"
            + "  X(geom) AS longitude,"
            + "  Y(geom) AS latitude,"
            + "  yearCounts"
            + "  FROM aves"
            + "  WHERE speciesKey=:speciesKey")
  List<PointFeature> getSpeciesCounts(@Bind("speciesKey") int speciesKey);

  @SqlQuery("SELECT"
            + "  X(geom) AS longitude,"
            + "  Y(geom) AS latitude,"
            + "  yearCounts"
            + "  FROM aves_group")
  List<PointFeature> getGroupCounts();

  @SqlQuery("SELECT speciesKey, scientificName FROM aves_names "
            + "WHERE scientificName like :prefix "
            + "ORDER BY scientificName LIMIT 25")
  List<ScientificName> autocomplete(@Bind("prefix") String prefix);

}
