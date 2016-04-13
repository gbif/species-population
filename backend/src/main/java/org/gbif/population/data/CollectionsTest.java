package org.gbif.population.data;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionsTest {
  private static final Logger LOG = LoggerFactory.getLogger(CollectionsTest.class);

  public static void main2(String[] args) {
    int numberFeatures = 10000000;
    int numberYears = 30;
    Random random = new Random();

    int minYear = 50;
    int maxYear = 88;
    int yearThreshold = 14;

    LOG.info("Creating random source data");
    Stopwatch timer = Stopwatch.createStarted();
    Collection<PointFeature> source = new ObjectBigArrayBigList(numberFeatures);
    for (int i=0; i<numberFeatures; i++) {
      Map<String, Integer> years = new Object2IntOpenHashMap();
      for (int j=0; j<numberYears; j++) {
        years.put(String.valueOf(random.nextInt(100)), 1 + random.nextInt(99));
      }
      source.add(new PointFeature(random.nextDouble(), random.nextDouble(), years));
    }

    LOG.info("Random source data created in {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();

    LOG.info("Filtering and copying to new collection");
    Collection<PointFeature> result = filterBy(source, minYear, maxYear, yearThreshold);
    LOG.info("Filtered to {} in {} msecs", result.size(), timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();

    LOG.info("Rewriting to new structure");
    Collection<Feature> source1 = rewrite1(source);
    LOG.info("Rewritten to {} in {} msecs", source1.size(), timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();

    LOG.info("Filtering and copying to new collection");
    Collection<Feature> result1 = filterBy1(source1, minYear, maxYear, yearThreshold);
    LOG.info("Filtered to {} in {} msecs", result1.size(), timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();

    LOG.info("Rewriting to new structure 2");
    ByteBuffer source2 = rewrite2(source);
    LOG.info("Rewritten in {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();

    LOG.info("Iterating test on a ByteBuffer");
    int matched = filterBy2(source2, (short)minYear, (short)maxYear, yearThreshold);
    LOG.info("Filtered to {} in {} msecs", matched, timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();



  }

  public static void main(String[] args) {
    int numberFeatures = 3000000;
    int numberYears = 30;
    Random random = new Random();

    int minYear = 20;
    int maxYear = 88;
    int yearThreshold = 14;

    LOG.info("Creating random source data");
    // buffer schema is encodedLat, encodedLng, year, count, year, count, -1
    // This is INT, INT, [SHORT, INT, SHORT] = 4 + 4 [2 + 4 +2]
    int totalSize = numberFeatures * (8 + numberYears*6 + 2);

    ByteBuffer source = ByteBuffer.allocateDirect(totalSize);
    for (int i=0; i<numberFeatures; i++) {
      source.putInt(encodeCoord(random.nextDouble()));
      source.putInt(encodeCoord(random.nextDouble()));
      for (int j=0; j<numberYears; j++) {
        source.putShort((short)random.nextInt(99)); // year
        source.putInt(random.nextInt(10000)); // count
      }
      source.putShort((short) -1);
    }
    LOG.info("Total bytes in the data {}", totalSize);
    source.rewind();

    Stopwatch timer = Stopwatch.createStarted();
    LOG.info("Iterating test on a ByteBuffer");
    int matched = filterBy2(source, (short)minYear, (short)maxYear, yearThreshold);
    LOG.info("Filtered to {} in {} msecs", matched, timer.elapsed(TimeUnit.MILLISECONDS));
    timer.reset().start();



  }

  private static int filterBy2(ByteBuffer data1, short minYear, short maxYear, int yearThreshold) {
    ByteBuffer data = data1.asReadOnlyBuffer();
    int matched = 0;
    while(data.hasRemaining()) {
      int lat = data.getInt();
      int lng = data.getInt();
      short year = 0;
      int yearsWithData = 0;
      while (year != (short) -1) {
        year = data.getShort();
        if (year != (short)-1) {
          int count = data.getInt();
          //LOG.info("{}:{}", year, count);

          if (year >= minYear && year <= maxYear) {
            yearsWithData++;
          }
        }
      }
      matched += yearsWithData>=yearThreshold ? 1 : 0;
      // buffer schema is encodedLat, encodedLng, year, count, year, count, -1
    }
    return matched;
  }

  private static Collection<Feature> rewrite1(Collection<PointFeature> source) {
    Collection<Feature> result = new ObjectBigArrayBigList(source.size());
    for (PointFeature pf : source) {
      Feature f = new Feature(pf.getLatitude(), pf.getLongitude());
      for (Map.Entry<String, Integer> e : pf.getYearCounts().entrySet()) {
        f.addYear(Integer.parseInt(e.getKey()), e.getValue());
      }
      result.add(f);
    }
    return result;
  }

  private static Collection<Feature> filterBy1(Collection<Feature> source, int minYear, int maxYear, int yearThreshold) {
    ObjectBigArrayBigList<Feature> result = new ObjectBigArrayBigList();
    for(Feature f : source) {
      int total = 0;
      for (int year = minYear; year<=maxYear; year++) {
        if (f.getYears().size()>year && f.getYears().get(year) != 0) {
          total++;
        }
      }
      if (total >= yearThreshold) {
        int upper = maxYear>f.getYears().size()-1 ? f.getYears().size()-1 : maxYear;
        result.add(new Feature(f.getLat(), f.getLng(), f.getYears().subList(minYear, upper)));
      }
    }
    return result;
  }

  /**
   * Returns a mutable copy of the source which is filtered to only those features who have years within the range and
   * enough years to satisfy the threshold.
   */
  private static Collection<PointFeature> filterBy(Collection<PointFeature> source, int minYear, int maxYear, int yearThreshold) {

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

  private static class Feature {
    private final double lat;
    private final double lng;
    private final IntBigList years;

    private Feature(double lat, double lng) {
      this.lat = lat;
      this.lng = lng;
      this.years = new IntBigArrayBigList();
    }

    private Feature(double lat, double lng, IntBigList years) {
      this.lat = lat;
      this.lng = lng;
      this.years = years;
    }

    public double getLat() {
      return lat;
    }

    public double getLng() {
      return lng;
    }

    public IntBigList getYears() {
      return years;
    }

    public void addYear(int position, int count) {
      // grow the arraylist until the size we need
      for (int i=years.size(); i<= position + 1; i++) {
        years.add(0);
      }
      years.set(position, count);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Feature that = (Feature) o;

      return Objects.equal(this.lat, that.lat) &&
             Objects.equal(this.lng, that.lng) &&
             Objects.equal(this.years, that.years);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(lat, lng, years);
    }
  }

  private static ByteBuffer rewrite2(Collection<PointFeature> source) {
    // buffer schema is encodedLat, encodedLng, year, count, year, count, -1
    // This is INT, INT, [SHORT, INT], SHORT

    // this is f'ugly.
    int totalSize = 0;
    for (PointFeature pf : source) {
      totalSize+=8; // encoded lat,lng
      totalSize+= pf.getYearCounts().size() * (2 + 4); // year,count pairs
      totalSize+=2; // end marker
    }

    LOG.info("Total bytes in the data {}", totalSize);

    ByteBuffer data = ByteBuffer.allocateDirect(totalSize);
    for (PointFeature pf : source) {
      data.putInt(encodeCoord(pf.getLatitude()));
      data.putInt(encodeCoord(pf.getLongitude()));
      for (Map.Entry<String, Integer> e : pf.getYearCounts().entrySet()) {
        data.putShort(Short.parseShort(e.getKey()));
        data.putInt(e.getValue());
      }
      data.putShort((short)-1);
    }
    data.rewind();
    return data.asReadOnlyBuffer();
  }

  private static int encodeCoord(double coord) {
    return (int) Math.floor(coord * 100);
  }
}
