package org.gbif.population.data;

import com.google.common.base.Objects;

public class DatasetName {

  private final String key;
  private final String name;

  public DatasetName(String key, String name) {
    this.key = key;
    this.name = name;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DatasetName that = (DatasetName) o;

    return Objects.equal(this.key, that.key) &&
           Objects.equal(this.name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key, name);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("key", key)
                  .add("name", name)
                  .toString();
  }
}
