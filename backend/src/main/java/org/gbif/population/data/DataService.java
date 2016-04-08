package org.gbif.population.data;

import java.awt.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataService {
  private static final Logger LOG = LoggerFactory.getLogger(DataService.class);
  private static final int SPECIES_CACHE_SIZE = 1000;
  private static final int GROUP_CACHE_SIZE = 1; // since no geometry in query, there is just one for now
  private final DataDAO dataDAO;

  /**
   * A caching service to read the species features.
   */
  private final LoadingCache<SpeciesCacheKey, List<PointFeature>> speciesFeaturesCache =
    CacheBuilder.newBuilder().maximumSize(SPECIES_CACHE_SIZE).build(
      new CacheLoader<SpeciesCacheKey, List<PointFeature>>() {
        @Override
        public List<PointFeature> load(SpeciesCacheKey key) throws Exception {
          return dataDAO.getSpeciesCounts(key.getSpeciesKey());
        }
      }
    );

  /**
   * A caching service to read the group features.
   */
  private final LoadingCache<GroupCacheKey, List<PointFeature>> groupFeaturesCache =
    CacheBuilder.newBuilder().maximumSize(GROUP_CACHE_SIZE).build(
      new CacheLoader<GroupCacheKey, List<PointFeature>>() {
        @Override
        public List<PointFeature> load(GroupCacheKey key) throws Exception {
          return dataDAO.getGroupCounts();
        }
      }
    );

  public DataService(DataDAO dataDAO) {this.dataDAO = dataDAO;}

  public List<PointFeature> getSpeciesFeatures(int speciesKey, int minYear, int maxYear, int yearThreshold) {
    try {
      // copy required to allow pruning
      List<PointFeature> features = deepCopyOf(speciesFeaturesCache.get(new SpeciesCacheKey(speciesKey)));
      // return a pruned list of years outside of the range and meeting the threshold requirements
      for (PointFeature feature : features) {
        filterByYearRange(feature.getYearCounts(), minYear, maxYear);
      }
      return Lists.newArrayList(filterByYearThreshold(features, yearThreshold));

    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  public List<PointFeature> getGroupFeatures(int minYear, int maxYear) {
    try {
      // copy required to allow pruning
      List<PointFeature> features = deepCopyOf(groupFeaturesCache.get(GroupCacheKey.getInstance()));
      // return a pruned list of years outside of the range and meeting the threshold requirements
      for (PointFeature feature : features) {
        filterByYearRange(feature.getYearCounts(), minYear, maxYear);
      }
      return features;
    } catch (ExecutionException e) {
      throw Throwables.propagate(e);
    }
  }

  /**
   * @return A true deep mutable copy to allow subsequent pruning
   */
  private List<PointFeature> deepCopyOf(List<PointFeature> source) {
    List<PointFeature> target = Lists.newArrayList();
    for (PointFeature f : source) {
      // take a copy of the map to allow pruning
      Map<String, Integer> yearCounts = Maps.newHashMap(ImmutableMap.copyOf(f.getYearCounts()));
      target.add(new PointFeature(f.getLatitude(), f.getLongitude(), yearCounts));
    }
    return target;
  }

  public List<ScientificName> autocomplete(String prefix) {
    return dataDAO.autocomplete(prefix);
  }

  /**
   * Filters out years outside of the range of interest.
   */
  private static void filterByYearRange(Map<String,Integer> years, final int minYear, final int maxYear) {
    Iterator<Map.Entry<String, Integer>> iter = years.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, Integer> year = iter.next();
      if (Integer.parseInt(year.getKey()) < minYear || Integer.parseInt(year.getKey()) > maxYear) {
        iter.remove();
      }
    }
  }

  /**
   * Filters features that do not have long enough time series data.
   */
  private static Iterable<PointFeature> filterByYearThreshold(List<PointFeature> features, final int yearThreshold) {
    return Iterables.filter(features, new Predicate<PointFeature>() {
      @Override
      public boolean apply(@Nullable PointFeature feature) {
        return feature.getYearCounts().size() >= yearThreshold;
      }
    });
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
