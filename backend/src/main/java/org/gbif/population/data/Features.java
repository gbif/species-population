package org.gbif.population.data;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.constraints.AssertTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A highly optimised direct memory backed structure to hold point feature data.
 * Experimental - use at your own risk(!)
 *
 * The data is encoded thusly:
 *
 * A repetition of the following for each feature:
 *   - latitude encoded as INT
 *   - longitude encoded as INT
 *   - A repetition of the following for each year within the feature:
 *     - year as SHORT
 *     - count as INT
 *   - An end marker of "-1" as SHORT (since year cannot be -1)
 */
@NotThreadSafe
public class Features {
  private static final Logger LOG = LoggerFactory.getLogger(Features.class);
  private static final int INITIAL_CAPACITY_BYTES = 8 * 1024 * 1024; // 8MB
  private static final short END_MARKER = (short) -1;

  private ByteBuffer data;
  private final int growthSizeBytes;

  private Features(int initialCapacity, int growthSizeBytes) {
    data = ByteBuffer.allocateDirect(INITIAL_CAPACITY_BYTES);
    this.growthSizeBytes = growthSizeBytes;
  }

  public static Features newInstance() {
    // double in size on each growth
    return new Features(INITIAL_CAPACITY_BYTES, INITIAL_CAPACITY_BYTES);
  }

  public static Features newInstance(int initialCapacity, int growthSizeBytes) {
    Preconditions.checkArgument(growthSizeBytes >= 16, "Must be able to grow at a rate of at least 16 bytes at a time");
    return new Features(initialCapacity, growthSizeBytes);
  }

  /**
   * A utility builder that will convert the Collection of PointFeatures into the highly optimized version.
   */
  public static Features buildFrom(Collection<PointFeature> features, int initialCapacity, int growthSizeBytes) {
    Features f = newInstance(initialCapacity, growthSizeBytes);
    LOG.info("Building from {} source features", features.size());
    Set<String> years = Sets.newHashSet();
    for (PointFeature pf : features) {
      f.startFeature(pf.getLatitude(), pf.getLongitude());
      //LOG.info("{},{}", pf.getLatitude(), pf.getLongitude());
      for (Map.Entry<String, Integer> year : pf.getYearCounts().entrySet()) {
        years.add(year.getKey());
        f.appendYear(year.getKey(), year.getValue());
      }
      f.endFeature();
    }
    f.finishWriting();
    return f;
  }

  /**
   * Creates a new point feature, which can be appended to, and MUST be closed.
   */
  public void startFeature(double latitude, double longitude) {
    // ensure space for 2 INTs (4 + 4), 1 year (2 + 4) and the closing marker (2)
    ensureBufferSpace(16);
    data.putInt(encodeCoord(latitude));
    data.putInt(encodeCoord(longitude));
  }

  public void appendYear(int year, int count) {
    appendYear((short)year, count);
  }

  public void appendYear(String year, int count) {
    appendYear(Short.parseShort(year), count);
  }

  public void appendYear(short year, int count) {
    // ensure space 1 year (2 + 4)
    ensureBufferSpace(6);
    data.putShort(year);
    data.putInt(count);
  }

  public void endFeature() {
    // ensure space for the closing marker (2)
    ensureBufferSpace(2);
    data.putShort(END_MARKER);
  }

  /**
   * Must be called when the writing is complete.
   */
  public void finishWriting() {
    data.limit(data.position());
    data.rewind();
    data = data.asReadOnlyBuffer();
  }

  /**
   * Ensures that the buffer will be big enough to accept the required size of data.  If not, it is grown.
   */
  private void ensureBufferSpace(int requiredSize) {
    if (data.remaining() < requiredSize) {
      LOG.info("Buffer out of space.  Growing by {}", growthSizeBytes);
      ByteBuffer copy = ByteBuffer.allocateDirect(data.capacity() + growthSizeBytes);
      int position = data.position();
      data.rewind();
      copy.put(data);
      copy.position(position);
      data = copy;
    }
  }

  /**
   * 2 decimal place rounding.
   */
  private static int encodeCoord(double coord) {
    return (int) Math.floor(coord * 100);
  }

  /**
   * Decodes the coordinates.
   */
  private static double decodeCoord(int encoded) {
    return ((double)encoded) / 100;
  }

  public FeaturesReader openReader() {
    return new FeaturesReader(this.data);
  }

  /**
   * A reader to help read a created buffer.
   */
  public static class FeaturesReader {
    private final ByteBuffer data;

    /**
     * The provided buffer is copied as a read only buffer.
     * However, any changes to the buffer after a read is started will result in unpredictable behaviour!
     */
    private FeaturesReader(ByteBuffer source) {
      this.data = source.asReadOnlyBuffer();
      this.data.rewind();
    }

    /**
     * @return an array of lat,lng
     */
    public double[] readLocation() {
      return new double[] {
        decodeCoord(data.getInt()), // lat
        decodeCoord(data.getInt())  // lng
      };
    }
    /**
     * @return a map of year:count
     */
    public Map<Short, Integer> readYears() {
      Map<Short, Integer> years = new Short2IntOpenHashMap();
      short year = data.getShort();
      int yearCount = 0;
      while (year != END_MARKER) {
        years.put(year, data.getInt());
        year = data.getShort();
        yearCount++;
      }
      return years;
    }

    public boolean hasNext() {
      return data.hasRemaining();
    }
  }
}
