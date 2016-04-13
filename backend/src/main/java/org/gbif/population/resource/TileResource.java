package org.gbif.population.resource;


import org.gbif.population.data.DataDAO;
import org.gbif.population.data.DataService;
import org.gbif.population.data.PointFeature;
import org.gbif.population.data.ScientificName;
import org.gbif.population.data.YearFeature;
import org.gbif.population.utils.MercatorProjection;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import no.ecc.vectortile.VectorTileEncoder;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.codetome.hexameter.core.api.DefaultSatelliteData;
import org.codetome.hexameter.core.api.Hexagon;
import org.codetome.hexameter.core.api.SatelliteData;
import org.codetome.hexameter.core.backport.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.codetome.hexameter.core.api.HexagonalGridLayout;
import org.codetome.hexameter.core.api.HexagonOrientation;
import org.codetome.hexameter.core.api.HexagonalGrid;
import org.codetome.hexameter.core.api.HexagonalGridBuilder;
import rx.Observable;
import rx.functions.Action1;

/**
 * A simple resource that returns a demo tile.
 */
abstract class TileResource {

  private static final Logger LOG = LoggerFactory.getLogger(TileResource.class);

  protected static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
  protected static final ObjectMapper MAPPER = new ObjectMapper();
  protected final DataService dataService;

  public TileResource(DataService dataService) {
    this.dataService = dataService;
  }

  /**
   * Returns the data as points.
   */
  public byte[] points(
    int z, int x, int y, int speciesKey,
    int minYear, int maxYear,
    final int yearThreshold, HttpServletResponse response
  ) throws IOException {
    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    Collection<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, yearThreshold);
    if (!speciesFeatures.isEmpty()) {
      Map<Point2D, Map<String, Integer>> groupFeatures = getIndexedGroupFeatures(minYear, maxYear);
      LOG.info("Found {} features in total for the group", groupFeatures.size());

      VectorTileEncoder encoder = new VectorTileEncoder(MercatorProjection.TILE_SIZE, 0, false);
      // world pixel addressing of the tile boundary, with 0,0 at top left
      int minTilePixelX = MercatorProjection.TILE_SIZE * x;
      int minTilePixelY = MercatorProjection.TILE_SIZE * y;

      for (PointFeature feature : speciesFeatures) {
        double pixelX = MercatorProjection.longitudeToPixelX(feature.getLongitude(), (byte) z);
        double pixelY = MercatorProjection.latitudeToPixelY(feature.getLatitude(), (byte) z);

        // trim to features actually on the tile
        if (pixelX >= minTilePixelX && pixelX < minTilePixelX + MercatorProjection.TILE_SIZE &&
            pixelY >= minTilePixelY && pixelY < minTilePixelY + MercatorProjection.TILE_SIZE
          ) {

          // find the pixel offsets local to the top left of the tile
          int[] tileLocalXY = new int[] {(int)Math.floor(pixelX%MercatorProjection.TILE_SIZE),
            (int)Math.floor(pixelY%MercatorProjection.TILE_SIZE)};

          Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(tileLocalXY[0], tileLocalXY[1]));
          Map<String, Object> meta = new Object2ObjectOpenHashMap();
          Map<String, Integer> groupYearCounts = groupFeatures.get(new Point2D.Double(feature.getLongitude(), feature.getLatitude()));
          // infer absence
          for (String year : groupYearCounts.keySet()) {
            if (!feature.getYearCounts().containsKey(year)) {
              feature.getYearCounts().put(year, 0);
            }
          }

          SimpleRegression regression = new SimpleRegression();
          // regression require 2 points minimum
          if (feature.getYearCounts().size()>2) {
            for (String year : feature.getYearCounts().keySet()) {
              double speciesCount = feature.getYearCounts().get(year);
              double groupCount = groupFeatures.get(new Point2D.Double(feature.getLongitude(),
                                                                       feature.getLatitude())).get(year);
              double normalizedCount = speciesCount / groupCount;
              regression.addData(Double.valueOf(year), normalizedCount);
            }
          }
          meta.put("slope", regression.getSlope());
          meta.put("intercept", regression.getIntercept());
          meta.put("significance", regression.getSignificance());
          meta.put("SSE", regression.getSumSquaredErrors());
          meta.put("interceptStdErr", regression.getInterceptStdErr());
          meta.put("meanSquareError", regression.getMeanSquareError());
          meta.put("slopeStdErr", regression.getSlopeStdErr());
          meta.put("groupCounts", MAPPER.writeValueAsString(groupYearCounts));
          meta.put("speciesCounts", MAPPER.writeValueAsString(feature.getYearCounts()));
          encoder.addFeature("points", meta, point);
        }
      }
      return encoder.encode();
    }
    return null; // not enough data
  }

  /**
   * Returns the features for the group, indexed by the geometery.
   */
  private Map<Point2D, Map<String, Integer>> getIndexedGroupFeatures(int minYear,int maxYear) {
    Map<Point2D, Map<String, Integer>> groupFeatures = new Object2ObjectOpenHashMap();
    Collection<PointFeature> group = dataService.getGroupFeatures(minYear, maxYear, 1); // require at least 1 year of data
    for (PointFeature f : group) {
      groupFeatures.put(new Point2D.Double(f.getLongitude(), f.getLatitude()), f.getYearCounts());
    }
    return groupFeatures;
  }

  public List<ScientificName> autocomplete(String prefix, HttpServletResponse response) {
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    String queryParam = prefix.endsWith("%") ? prefix : prefix + "%";
    return dataService.autocomplete(queryParam);
  }

  /**
   * Perfroms an ad hoc regression based on the input parameters.
   */
  public Map<String, Object> adhocRegression(
    int speciesKey, int minYear, int maxYear,
    int yearThreshold, double minLatitude, double maxLatitude,
    double minLongitude, double maxLongitude, HttpServletResponse response
  ) throws IOException {
    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");
    Map<String, Object> data = new Object2ObjectOpenHashMap(); // response

    Collection<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, 1);
    if (!speciesFeatures.isEmpty()) {
      Collection<PointFeature> groupFeatures = dataService.getGroupFeatures(minYear, maxYear, 1); // require at least 1 year of data

      Map<String, AtomicInteger> speciesCounts = new Object2ObjectOpenHashMap();
      Map<String, AtomicInteger> groupCounts = new Object2ObjectOpenHashMap();
      collectPoints(minLatitude, maxLatitude, minLongitude, maxLongitude, speciesFeatures, speciesCounts);
      collectPoints(minLatitude, maxLatitude, minLongitude, maxLongitude, groupFeatures, groupCounts);
      LOG.info("Found {} years with species and {} years with groups", speciesCounts.size(), groupCounts.size());

      SimpleRegression regression = new SimpleRegression();

      // use the group counts, since we wish to infer absence (0 count) for years where there are records within
      // the group but the species is not recorded for that year.
      for (String year : groupCounts.keySet()) {
        double speciesCount = speciesCounts.containsKey(year) ? speciesCounts.get(year).doubleValue() : 0.0;
        double normalizedCount = speciesCount / groupCounts.get(year).doubleValue();
        regression.addData(Double.valueOf(year), normalizedCount);
        // add to the data that we've inferred a 0
        if (!speciesCounts.containsKey(year)) {
          speciesCounts.put(year, new AtomicInteger(0));
        }
      }
      data.put("slope", regression.getSlope());
      data.put("intercept", regression.getIntercept());
      data.put("significance", regression.getSignificance());
      data.put("SSE", regression.getSumSquaredErrors());
      data.put("interceptStdErr", regression.getInterceptStdErr());
      data.put("meanSquareError", regression.getMeanSquareError());
      data.put("slopeStdErr", regression.getSlopeStdErr());
      data.put("groupCounts", groupCounts);
      data.put("speciesCounts", speciesCounts);
    } else {
      LOG.info("Species response is empty");
    }
    return data;
  }

  /**
   * Collects the year data for the source features into the target map.
   */
  private void collectPoints(
    double minLatitude,
    double maxLatitude,
    double minLongitude,
    double maxLongitude,
    Collection<PointFeature> source,
    Map<String, AtomicInteger> target
  ) {
    for (PointFeature feature : source) {
      if (feature.getLongitude() >= minLongitude && feature.getLongitude() <= maxLongitude
          && feature.getLatitude() >= minLatitude && feature.getLatitude() <= maxLatitude) {

        for (Map.Entry<String, Integer> e : feature.getYearCounts().entrySet()) {
          if (target.containsKey(e.getKey())) {
            target.get(e.getKey()).getAndAdd(e.getValue());
          } else {
            target.put(e.getKey(), new AtomicInteger(e.getValue()));
          }
        }
      }
    }
  }

  /**
   * Returns the data as hex grids.
   */
  public byte[] hex(
    final int z, final int x, final int y, int speciesKey,
    int minYear, int maxYear,
    int yearThreshold, Integer hexRadius, HttpServletResponse response
  ) throws IOException {

    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    // We cannot pass a year threshold here since we are grouping to the hexagon, and need to apply the threshold to
    // the hexagon in total.  There should be no features with 0 years, but we use 1 since a point with no year data
    // within our time period makes no sense.
    Collection<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, 1);
    LOG.debug("Found {} features", speciesFeatures.size());

    if (!speciesFeatures.isEmpty()) {
      // 16x decrease due to painting on large vector tiles.  Picks a sensible default
      final double radius = hexRadius == null ? 80 : hexRadius*16;
      final double hexWidth = radius*2;
      final double hexHeight = Math.sqrt(3) * radius;

      final VectorTileEncoder encoder = new VectorTileEncoder(MercatorProjection.TILE_SIZE, 8, false);

      // World pixel addressing of the tile boundary, with 0,0 at top left
      int minTilePixelX = MercatorProjection.TILE_SIZE * x;
      int minTilePixelY = MercatorProjection.TILE_SIZE * y;

      // Set up the NxM grid of hexes, allowing for buffer of 2 hexagons all around.  If hexagons aligned perfectly
      // to the tile boundary a buffer of 1 would suffice.  However, a buffer of 2 allows us to move the grid to align
      // the hexagon polygons with the ones in the tile directly above and to the left.
      // The 3.0/2.5 factor is because we get 3 tiles in horizontal space of 2.5 widths due to the packing of hexagons
      int requiredWidth = (int)Math.ceil(MercatorProjection.TILE_SIZE * 3.0 / hexWidth * 2.5) + 4;
      int requiredHeight = (int)Math.ceil(MercatorProjection.TILE_SIZE / hexHeight) + 4;
      LOG.debug("Hex sizes {}x{} calculated grid {}x{}", hexWidth, hexHeight, requiredWidth, requiredHeight);

      HexagonalGrid grid = new HexagonalGridBuilder()
        .setGridWidth(requiredWidth)
        .setGridHeight(requiredHeight)
        .setGridLayout(HexagonalGridLayout.RECTANGULAR)
        .setOrientation(HexagonOrientation.FLAT_TOP)
        .setRadius(radius)
        .build();

      // Hexagons do not align at boundaries, and therefore we need to determine the offsets to ensure polygons
      // meet correctly across tiles.
      // The maximum offset is 1.5 cells horizontally and 1 cell vertically due to using flat top tiles.  This is
      // apparent when you see a picture. See this as an excellent resource
      // http://www.redblobgames.com/grids/hexagons/#basics
      final double offsetX = (x*((MercatorProjection.TILE_SIZE)%(1.5*hexWidth)))%(1.5*hexWidth);
      final double offsetY = (y*(MercatorProjection.TILE_SIZE%hexHeight))%hexHeight;

      // for each feature returned from the DB locate its hexagon and store the data on the hexagon
      Set<Hexagon> dataCells = new ObjectOpenHashSet();
      Stopwatch timer = Stopwatch.createStarted();
      for(PointFeature feature : speciesFeatures) {
        Hexagon hex = addFeatureInHex((byte) z,
                        hexWidth,
                        hexHeight,
                        minTilePixelX,
                        minTilePixelY,
                        grid,
                        offsetX,
                        offsetY,
                        feature,
                        "speciesCounts",
                        true);
        if (hex != null) {
          dataCells.add(hex);
        }
      }
      LOG.info("Adding species: {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
      timer.reset().start();
      // require at least 1 year of data within the range, or else it is a meaningless feature
      Collection<PointFeature> groupFeatures  = dataService.getGroupFeatures(minYear, maxYear, 1);
      LOG.info("Group lookup: {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
      timer.reset().start();
      for(PointFeature feature : groupFeatures) {
        addFeatureInHex((byte) z,
                        hexWidth,
                        hexHeight,
                        minTilePixelX,
                        minTilePixelY,
                        grid,
                        offsetX,
                        offsetY,
                        feature,
                        "groupCounts",
                        false); // only add group data to hexagons with species data (!)
      }
      LOG.info("Adding group: {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));


      LOG.info("{} hexagons with data", dataCells.size());
      timer.reset().start();
      for (Hexagon hexagon : dataCells) {

        // A hexagon is painted if it there is enough data.  We consider enough as having a user defined minimum number
        // of years with data for the species.
        SatelliteData data = hexagon.getSatelliteData().get(); // must exist
        Map<String,Integer> speciesCounts = data.<Map<String,Integer>>getCustomData("speciesCounts").get(); // must exist
        if (speciesCounts.size() >= yearThreshold) {
          Coordinate[] coordinates = new Coordinate[7];
          int i=0;
          for(org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {
            coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX - (hexWidth*1.5),
                                              point.getCoordinateY() - offsetY - (2*hexHeight));
          }
          coordinates[6] = coordinates[0]; // close our polygon
          LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
          Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

          Map<String,Integer> groupCounts = data.<Map<String,Integer>>getCustomData("groupCounts").get(); // must exist
          Map<String, Object> meta = new Object2ObjectOpenHashMap();

          // convert hexagon centers to global pixel space, and find the lat,lng centers
          meta.put("id",
                   roundThreeDecimals(MercatorProjection.pixelYToLatitude(minTilePixelY + hexagon.getCenterY() - offsetY - (2*hexHeight), (byte) z))
                   + "," +
                   roundThreeDecimals(MercatorProjection.pixelXToLongitude(minTilePixelX + hexagon.getCenterX() - offsetX - (1.5*hexWidth), (byte) z))
          );
          // One cannot regress a single point.
          if (groupCounts.size()>2) {
            SimpleRegression regression = new SimpleRegression();

            // use the group counts, since we wish to infer absence (0 count) for years where there are records within
            // the group but the species is not recorded for that year.
            for (String year : groupCounts.keySet()) {
              double speciesCount = speciesCounts.containsKey(year) ? (double) speciesCounts.get(year) : 0.0;
              double normalizedCount = speciesCount / groupCounts.get(year);
              regression.addData(Double.valueOf(year), normalizedCount);
              // add to the data that we've inferred a 0
              if (!speciesCounts.containsKey(year)) {
                speciesCounts.put(year, 0);
              }
            }
            meta.put("slope", regression.getSlope());
            meta.put("intercept", regression.getIntercept());
            meta.put("significance", regression.getSignificance());
            meta.put("SSE", regression.getSumSquaredErrors());
            meta.put("interceptStdErr", regression.getInterceptStdErr());
            meta.put("meanSquareError", regression.getMeanSquareError());
            meta.put("slopeStdErr", regression.getSlopeStdErr());
          }
          meta.put("groupCounts", MAPPER.writeValueAsString(groupCounts));
          meta.put("speciesCounts", MAPPER.writeValueAsString(speciesCounts));
          encoder.addFeature("hex", meta, poly);
        }
      }
      LOG.info("Setting up encoder: {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
      return encoder.encode();
    }

    return null;
  }

  /**
   * Adds a feature to the satellite data in the hexagon taking into account the offsets.
   * It should be noted that on a tile with 0 offset, the top left of the tile is actually covered by tile 1,0 (x,y)
   * and numbered on an odd-q vertical layout addressing scheme on http://www.redblobgames.com/grids/hexagons/.
   * @param z the zoom
   * @param hexWidth the width of a hexagon
   * @param hexHeight the height of a hexagon
   * @param minTilePixelX the minimum pixel X of the tile in world space
   * @param minTilePixelY the minimum pixel Y of the tile in world space
   * @param grid the hexagon grid
   * @param offsetX the offset for the hexagon to align with adjacent tiles
   * @param offsetY the offset for the hexagon to align with adjacent tiles
   * @param feature to inspect and add
   * @param satelliteDataKey to key on the metadata (satellite data) associated on the tile
   * @param canCreateSatelliteData if true, satellite data can be created, otherwise this will do nothing unless the
   *                               satellite data already exists (e.g. to add in group counts only if species counts
   *                               exists)
   * @return the hexagon or null when the hexagon is not on the hex grid or if satellite data is null and it cannot be
   * created.
   */
  protected Hexagon addFeatureInHex(
    byte z,
    double hexWidth,
    double hexHeight,
    int minTilePixelX,
    int minTilePixelY,
    HexagonalGrid grid,
    double offsetX,
    double offsetY,
    PointFeature feature,
    String satelliteDataKey,
    boolean canCreateSatelliteData
  ) {
    double pixelX = MercatorProjection.longitudeToPixelX(feature.getLongitude(), z);
    double pixelY = MercatorProjection.latitudeToPixelY(feature.getLatitude(), z);

    // trim to features that lie on the tile or within a hexagon buffer
    if (pixelX >= minTilePixelX - (1.5*hexWidth) && pixelX < minTilePixelX + MercatorProjection.TILE_SIZE + (1.5*hexWidth) &&
        pixelY >= minTilePixelY - (2*hexHeight) && pixelY < minTilePixelY + MercatorProjection.TILE_SIZE + (2*hexHeight)
      ) {

      // find the pixel offset local to the top left of the tile
      double[] tileLocalXY = new double[] {pixelX - minTilePixelX, pixelY - minTilePixelY};

      // and the pixel when on hex grid space, compensating for the offset and 2 hex buffer
      double[] hexGridLocalXY = new double[] {tileLocalXY[0] + offsetX + (1.5*hexWidth), tileLocalXY[1] + offsetY + (2*hexHeight)};

      Optional<Hexagon> hex = grid.getByPixelCoordinate(hexGridLocalXY[0], hexGridLocalXY[1]);
      if (hex.isPresent()) {
        Hexagon hexagon = hex.get();

        if (canCreateSatelliteData) {
          if (!hexagon.getSatelliteData().isPresent()) {
            hexagon.setSatelliteData(new DefaultSatelliteData());
          }
        }

        if (hexagon.getSatelliteData().isPresent()) {
          SatelliteData cellData = hexagon.getSatelliteData().get();

          if (!cellData.getCustomData(satelliteDataKey).isPresent()) {
            cellData.addCustomData(satelliteDataKey, feature.getYearCounts());
          } else {
            Map<String, Integer> existingCounts = (Map<String, Integer>) cellData.getCustomData(satelliteDataKey).get();
            for (Map.Entry<String, Integer> e : feature.getYearCounts().entrySet()) {
              int value = existingCounts.containsKey(e.getKey()) ?
                existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
              existingCounts.put(e.getKey(), value);
            }
          }
        }
        return hexagon;
      }
    }
    return null;
  }

  /**
   * Rounds to 3 decimal places
   */
  protected static double roundThreeDecimals(double d) {
    DecimalFormat twoDForm = new DecimalFormat("#.###");
    return Double.valueOf(twoDForm.format(d));
  }
}
