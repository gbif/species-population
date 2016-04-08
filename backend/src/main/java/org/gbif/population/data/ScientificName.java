package org.gbif.population.data;

import com.google.common.base.Objects;

public class ScientificName {
  private final int speciesKey;
  private final String scientificName;

  public ScientificName(int speciesKey, String scientificName) {
    this.speciesKey = speciesKey;
    this.scientificName = scientificName;
  }

  public int getSpeciesKey() {
    return speciesKey;
  }

  public String getScientificName() {
    return scientificName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ScientificName that = (ScientificName) o;

    return Objects.equal(this.speciesKey, that.speciesKey) &&
           Objects.equal(this.scientificName, that.scientificName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(speciesKey, scientificName);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("speciesKey", speciesKey)
                  .add("scientificName", scientificName)
                  .toString();
  }
}
