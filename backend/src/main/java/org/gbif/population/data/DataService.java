package org.gbif.population.data;

import java.awt.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataService {
  private static final Logger LOG = LoggerFactory.getLogger(DataService.class);
  private static final int SPECIES_CACHE_SIZE = 1000;
  private static final int GROUP_CACHE_SIZE = 100;
  private final DataDAO dataDAO;


  /**
   * A caching service to read the species features.
   */
  private final LoadingCache<SpeciesCacheKey, Collection<PointFeature>> speciesFeaturesCache =
    CacheBuilder.newBuilder().maximumSize(SPECIES_CACHE_SIZE).build(
      new CacheLoader<SpeciesCacheKey, Collection<PointFeature>>() {
        @Override
        public Collection<PointFeature> load(SpeciesCacheKey key) throws Exception {
          return new ObjectBigArrayBigList(dataDAO.getSpeciesCounts(key.getSpeciesKey()).iterator());
        }
      }
    );

  /**
   * A caching service to read the group features.
   */
  private final LoadingCache<GroupCacheKey, Features> groupFeaturesCache2 =
    CacheBuilder.newBuilder().maximumSize(GROUP_CACHE_SIZE).build(
      new CacheLoader<GroupCacheKey, Features>() {
        @Override
        public Features load(GroupCacheKey key) throws Exception {
          // 100Mb, growing at 10Mb size
          return Features.buildFrom(dataDAO.getGroupCounts(), 100 * 1024 * 1024, 10 * 1024 * 1024);
        }
      }
    );

  /**
   * A caching service to read the group features.
   */
  private final LoadingCache<GroupCacheKey, Collection<PointFeature>> groupFeaturesCache =
    CacheBuilder.newBuilder().maximumSize(GROUP_CACHE_SIZE).build(
      new CacheLoader<GroupCacheKey, Collection<PointFeature>>() {
        @Override
        public Collection<PointFeature> load(GroupCacheKey key) throws Exception {
          return new ObjectBigArrayBigList(dataDAO.getGroupCounts().iterator());
        }
      }
    );


  public DataService(DataDAO dataDAO) {this.dataDAO = dataDAO;}

  public Collection<PointFeature> getSpeciesFeatures(int speciesKey, int minYear, int maxYear, int yearThreshold) {
    try {
      return filterBy(speciesFeaturesCache.get(new SpeciesCacheKey(speciesKey)), minYear, maxYear, yearThreshold);
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  public Collection<PointFeature> getGroupFeatures(int minYear, int maxYear, int yearThreshold) {
    try {
      // require at least 1 year of data
      return filterBy(groupFeaturesCache.get(GroupCacheKey.getInstance()), minYear, maxYear, yearThreshold);
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  private Object groupLock = new Object();
  public Features.FeaturesReader getGroupFeatures() {
    try {
      Features features = null;
      synchronized (groupLock) {
        features = groupFeaturesCache2.get(GroupCacheKey.getInstance());
      }

      return features.openReader();

    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  public List<ScientificName> autocomplete(String prefix) {
    return dataDAO.autocomplete(prefix);
  }

  /**
   * Returns a mutable copy of the source which is filtered to only those features who have years within the range and
   * enough years to satisfy the threshold.
   */
  private Collection<PointFeature> filterBy(Collection<PointFeature> source, int minYear, int maxYear, int yearThreshold) {

    ObjectBigArrayBigList<PointFeature> result = new ObjectBigArrayBigList();
    for(PointFeature f : source) {

      Map<String, Integer> yearCopy = new Object2IntOpenHashMap();
      for (Map.Entry<String, Integer> e : f.getYearCounts().entrySet()) {
        if (Integer.parseInt(e.getKey()) >= minYear && Integer.parseInt(e.getKey()) <= maxYear) {
          yearCopy.put(e.getKey(), e.getValue());
        }
      }
      if (yearCopy.size()>=yearThreshold) {
        result.add(new PointFeature(f.getLatitude(), f.getLongitude(), yearCopy));
      }
    }
    return result;
  }

  /**
   * Placeholder for future use where we might wish to e.g. not have static data, or make use of geom queries.
   */
  private static class GroupCacheKey {
    private final static GroupCacheKey INSTANCE = new GroupCacheKey();

    // not for use
    private GroupCacheKey() {
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GroupCacheKey that = (GroupCacheKey) o;
      return Objects.equal(this.INSTANCE, that.INSTANCE);
    }

    @Override
    public int hashCode() {
      return 1;
    }

    static GroupCacheKey getInstance() {
      return INSTANCE;
    }

  }

  /**
   * Key object for the species cache.
   */
  private static class SpeciesCacheKey {
    private final int speciesKey;

    private SpeciesCacheKey(int speciesKey) {
      this.speciesKey = speciesKey;
    }

    public int getSpeciesKey() {
      return speciesKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SpeciesCacheKey that = (SpeciesCacheKey) o;
      return speciesKey == that.speciesKey;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(speciesKey);
    }
  }
}
