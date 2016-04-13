package org.gbif.population.data;

import java.util.List;

public interface DataDAO {

  List<PointFeature> getSpeciesCounts(int speciesKey);

  List<PointFeature> getGroupCounts();

  List<ScientificName> autocomplete(String prefix);
}
