package org.gbif.population.data;

import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class FeaturesTest {

  @Test
  public void testEndToEnd() {
    Features f = Features.newInstance();
    f.startFeature(44.0, -60.1);
    f.appendYear(1990, 1);
    f.appendYear(1991, 2);
    f.endFeature();
    f.startFeature(-20.0, -120.0);
    f.appendYear(1991, 1);
    f.appendYear(1992, 2);
    f.endFeature();

    f.finishWriting();

    Features.FeaturesReader reader = f.openReader();
    assertTrue("Reader has no data", reader.hasNext());
    assertArrayEquals("First location not equal", (double[])new double[]{44.0, -60.1}, reader.readLocation(), 0);
    Map<Short, Integer> years = reader.readYears();
    assertTrue("1990 should exist", years.containsKey((short)1990));
    assertEquals("1990 should be 1", (int) years.get((short)1990), 1);
    assertTrue("1991 should exist", years.containsKey((short)1991));
    assertEquals("1991 should be 2", (int) years.get((short)1991), 2);

    assertTrue("Reader has no second entry", reader.hasNext());
    assertArrayEquals("Second location not equal", (double[])new double[]{-20.0, -120.0}, reader.readLocation(), 0);
    years = reader.readYears();
    assertTrue("1991 should exist", years.containsKey((short)1991));
    assertEquals("1991 should be 1", (int) years.get((short)1991), 1);
    assertTrue("1992 should exist", years.containsKey((short)1992));
    assertEquals("1992 should be 2", (int) years.get((short)1992), 2);

    assertFalse("Reader should say it is finished", reader.hasNext());
  }

  @Test
  public void testGrow() {
    // 100 bytes growing at 10 bytes at a time
    Features f = Features.newInstance(10, 20);
    for (int i = 0; i<1000; i++) {
      f.startFeature(10.0, 10.0);
      f.appendYear(1990, 1);
      f.appendYear(1991, 2);
      f.endFeature();
    }
    f.finishWriting();
    Features.FeaturesReader reader = f.openReader();
    assertTrue("Reader has no data", reader.hasNext());

    int read = 0;
    while (reader.hasNext()) {
      reader.readLocation();
      reader.readYears();
      read++;
    }
    assertEquals("Unable to read all records", read, 1000);
  }
}
