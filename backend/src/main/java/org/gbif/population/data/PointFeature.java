package org.gbif.population.data;

import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class PointFeature {
  private final double latitude;
  private final double longitude;
  private final Map<Integer, Integer> speciesCounts = Maps.newHashMap();
  private final Map<Integer, Integer> groupCounts = Maps.newHashMap();

  public PointFeature(double latitude, double longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public Map<Integer, Integer> getGroupCounts() {
    return groupCounts;
  }

  public Map<Integer, Integer> getSpeciesCounts() {
    return speciesCounts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PointFeature that = (PointFeature) o;

    return Objects.equal(this.latitude, that.latitude) &&
           Objects.equal(this.longitude, that.longitude) &&
           Objects.equal(this.speciesCounts, that.speciesCounts) &&
           Objects.equal(this.groupCounts, that.groupCounts);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(latitude, longitude, speciesCounts, groupCounts);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("latitude", latitude)
                  .add("longitude", longitude)
                  .add("speciesCounts", speciesCounts)
                  .add("groupCounts", groupCounts)
                  .toString();
  }
}
