package org.gbif.population.data;

import com.google.common.base.Objects;

public class GridCount {

  private final int x;
  private final int y;
  private final int count;

  public GridCount(int x, int y, int count) {
    this.x = x;
    this.y = y;
    this.count = count;
  }

  public int getCount() {
    return count;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GridCount that = (GridCount) o;

    return Objects.equal(this.x, that.x) &&
           Objects.equal(this.y, that.y) &&
           Objects.equal(this.count, that.count);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(x, y, count);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
                  .add("x", x)
                  .add("y", y)
                  .add("count", count)
                  .toString();
  }
}
