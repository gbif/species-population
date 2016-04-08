package org.gbif.population.data;

import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;

public class PointFeature {
  private final double latitude;
  private final double longitude;
  private final Map<String, Integer> yearCounts;

  public PointFeature(double latitude, double longitude, Map<String, Integer> yearCounts) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.yearCounts = yearCounts;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public Map<String, Integer> getYearCounts() {
    return yearCounts;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PointFeature that = (PointFeature) o;

    return Objects.equal(this.latitude, that.latitude) &&
           Objects.equal(this.longitude, that.longitude) &&
           Objects.equal(this.yearCounts, that.yearCounts);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(latitude, longitude, yearCounts);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("latitude", latitude)
                  .add("longitude", longitude)
                  .add("yearCounts", yearCounts)
                  .toString();
  }
}
