package org.gbif.population.data;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

@RegisterMapper({GridCountMapper.class, DatasetNameMapper.class})
public interface DataDAO {

  @SqlQuery("SELECT x1, y1, count(DISTINCT dataset_key) AS count FROM tiles "
            + "WHERE x=:x AND y=:y AND z=:z AND year BETWEEN :minYear AND :maxYear "
            + "GROUP BY x1,y1")
  List<GridCount> lookup(
    @Bind("z") int z, @Bind("x") int x, @Bind("y") int y, @Bind("minYear")
  int minYear, @Bind("maxYear") int maxYear
  );

  @SqlQuery("SELECT t.dataset_key, title AS name "
            + "FROM tiles t JOIN datasets d ON t.dataset_key=d.dataset_key "
            + "WHERE x=:x AND y=:y AND z=:z AND x1=:x1 AND y1=:y1 AND year BETWEEN :minYear AND :maxYear "
            + "GROUP BY dataset_key, name "
            + "ORDER BY name")
  List<DatasetName> datasets(
    @Bind("z") int z, @Bind("x") int x, @Bind("y") int y, @Bind("x1") int x1,
    @Bind("y1") int y1, @Bind("minYear") int minYear, @Bind("maxYear") int maxYear
  );

}
