package org.gbif.population.data;

import com.google.common.base.Objects;

public class YearFeature {
  private final int year;
  private final int speciesCount;
  private final int groupCount;

  public YearFeature(int year, int speciesCount, int groupCount) {
    this.year = year;
    this.speciesCount = speciesCount;
    this.groupCount = groupCount;
  }

  public int getGroupCount() {
    return groupCount;
  }

  public int getSpeciesCount() {
    return speciesCount;
  }

  public int getYear() {
    return year;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    YearFeature that = (YearFeature) o;

    return Objects.equal(this.year, that.year) &&
           Objects.equal(this.speciesCount, that.speciesCount) &&
           Objects.equal(this.groupCount, that.groupCount);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(year, speciesCount, groupCount);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("year", year)
                  .add("speciesCount", speciesCount)
                  .add("groupCount", groupCount)
                  .toString();
  }
}
