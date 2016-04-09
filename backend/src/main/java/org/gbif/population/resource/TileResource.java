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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
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
@Path("/")
@Singleton
public final class TileResource {

  private static final Logger LOG = LoggerFactory.getLogger(TileResource.class);

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final DataService dataService;

  public TileResource(DataService dataService) {
    this.dataService = dataService;
  }

  /**
   * Returns the data as points.
   */
  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/points.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] points(
    @PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y, @PathParam("speciesKey") int speciesKey,
    @QueryParam("minYear") int minYear, @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") final int yearThreshold, @Context HttpServletResponse response
  ) throws IOException {
    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    List<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, yearThreshold);
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
          Map<String, Object> meta = Maps.newHashMap();
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
    Map<Point2D, Map<String, Integer>> groupFeatures = Maps.newHashMap();
    List<PointFeature> group = dataService.getGroupFeatures(minYear, maxYear);
    for (PointFeature f : group) {
      groupFeatures.put(new Point2D.Double(f.getLongitude(), f.getLatitude()), f.getYearCounts());
    }
    return groupFeatures;
  }

  @GET
  @Path("/autocomplete")
  @Timed
  @Produces("application/json")
  public List<ScientificName> autocomplete(@QueryParam("prefix") String prefix, @Context HttpServletResponse response) {
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    String queryParam = prefix.endsWith("%") ? prefix : prefix + "%";
    return dataService.autocomplete(queryParam);
  }

  /**
   * Perfroms an ad hoc regression based on the input parameters.
   */
  @GET
  @Path("{speciesKey}/regression.json")
  @Timed
  @Produces("application/json")
  public Map<String, Object> adhocRegression(
    @PathParam("speciesKey") int speciesKey, @QueryParam("minYear") int minYear, @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") int yearThreshold, @QueryParam("minLat") double minLatitude, @QueryParam("maxLat") double maxLatitude,
    @QueryParam("minLng") double minLongitude, @QueryParam("maxLng") double maxLongitude, @Context HttpServletResponse response
  ) throws IOException {
    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    List<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, yearThreshold);
    if (!speciesFeatures.isEmpty()) {
      Map<Point2D, Map<String, Integer>> groupFeatures = getIndexedGroupFeatures(minYear, maxYear);
      LOG.info("Found {} features in total for the group", groupFeatures.size());

      SimpleRegression regression = new SimpleRegression();
      Map<Double, Double> speciesCounts = Maps.newHashMap();
      Map<Double, Double> groupCounts = Maps.newHashMap();
      for (PointFeature feature : speciesFeatures) {
        if (feature.getLongitude() >= minLongitude && feature.getLongitude() <= maxLongitude
            && feature.getLatitude() >= minLatitude && feature.getLatitude() <= maxLatitude) {

          // regression require 2 points minimum
          if (feature.getYearCounts().size()>2) {
            for (String year : feature.getYearCounts().keySet()) {
              double speciesCount = feature.getYearCounts().get(year);
              double groupCount = groupFeatures.get(new Point2D.Double(feature.getLongitude(),
                                                                       feature.getLatitude())).get(year);

              // TODO: merge data
            }
          }
        }
      }
    }

    Map<String, Object> data = Maps.newHashMap();
    return data;
  }

  /**
   * Returns the data as hex grids.
   */
  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/hex.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] hex(
    @PathParam("z") final int z, @PathParam("x") final int x, @PathParam("y") final int y, @PathParam("speciesKey") int speciesKey,
    @QueryParam("minYear") int minYear, @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") Integer yearThreshold, @QueryParam("radius") Integer hexRadius, @Context HttpServletResponse response
  ) throws IOException {

    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    // we always search for all data regardless of the threshold supplied, but then filter out afterwards
    List<PointFeature> speciesFeatures = dataService.getSpeciesFeatures(speciesKey, minYear, maxYear, 0);
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

      HexagonalGridBuilder builder = new HexagonalGridBuilder()
        .setGridWidth(requiredWidth)
        .setGridHeight(requiredHeight)
        .setGridLayout(HexagonalGridLayout.RECTANGULAR)
        .setOrientation(HexagonOrientation.FLAT_TOP)
        .setRadius(radius);

      HexagonalGrid grid = builder.build();

      // Hexagons do not align at boundaries, and therefore we need to determine the offsets to ensure polygons
      // meet correctly across tiles.
      // The maximum offset is 1.5 cells horizontally and 1 cell vertically due to using flat top tiles.  This is
      // apparent when you see a picture. See this as an excellent resource
      // http://www.redblobgames.com/grids/hexagons/#basics
      final double offsetX = (x*((MercatorProjection.TILE_SIZE)%(1.5*hexWidth)))%(1.5*hexWidth);
      final double offsetY = (y*(MercatorProjection.TILE_SIZE%hexHeight))%hexHeight;

      // for each feature returned from the DB locate its hexagon and store the data on the hexagon
      Set<Hexagon> dataCells = Sets.newHashSet();
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

      List<PointFeature> groupFeatures  = dataService.getGroupFeatures(minYear, maxYear);
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

      LOG.info("{} hexagons with data", dataCells.size());

      for (Hexagon hexagon : dataCells) {
        Coordinate[] coordinates = new Coordinate[7];
        int i=0;
        for(org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {
          coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX - (hexWidth*1.5),
                                            point.getCoordinateY() - offsetY - (2*hexHeight));
        }
        coordinates[6] = coordinates[0]; // close our polygon
        LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

        SatelliteData data = hexagon.getSatelliteData().get(); // must exist
        Map<String,Integer> groupCounts = data.<Map<String,Integer>>getCustomData("groupCounts").get();

        Map<String, Object> meta = Maps.newHashMap();

        // convert hexagon centers to global pixel space, and find the lat,lng centers
        meta.put("id",
                 roundThreeDecimals(MercatorProjection.pixelYToLatitude(minTilePixelY + hexagon.getCenterY() - offsetY - (2*hexHeight), (byte) z))
                 + "," +
                 roundThreeDecimals(MercatorProjection.pixelXToLongitude(minTilePixelX + hexagon.getCenterX() - offsetX - (1.5*hexWidth), (byte) z))
                 );

        meta.put("groupCounts", MAPPER.writeValueAsString(groupCounts));

        Optional<Map<String,Integer>> optionalSpeciesCounts = data.getCustomData("speciesCounts");
        if (optionalSpeciesCounts.isPresent()) {
          Map<String,Integer> speciesCounts = optionalSpeciesCounts.get();
          // infer absence
          for (String year : groupCounts.keySet()) {
            if (!speciesCounts.containsKey(year)) {
              speciesCounts.put(year, 0);
            }
          }
          // Only regress those where there is enough points for the threshold, and at least 2 (since you can't regress
          // one point)
          if (speciesCounts.size() >= yearThreshold && speciesCounts.size()>2) {
            SimpleRegression regression = new SimpleRegression();
            for (String year : speciesCounts.keySet()) {
              double normalizedCount = ((double) speciesCounts.get(year)) /
                                       groupCounts.get(year);
              regression.addData(Double.valueOf(year), normalizedCount);
            }
            meta.put("slope", regression.getSlope());
            meta.put("intercept", regression.getIntercept());
            meta.put("significance", regression.getSignificance());
            meta.put("SSE", regression.getSumSquaredErrors());
            meta.put("interceptStdErr", regression.getInterceptStdErr());
            meta.put("meanSquareError", regression.getMeanSquareError());
            meta.put("slopeStdErr", regression.getSlopeStdErr());
          }
          meta.put("speciesCounts", MAPPER.writeValueAsString(speciesCounts));
        }

        encoder.addFeature("hex", meta, poly);
      }

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
  private Hexagon addFeatureInHex(
    @PathParam("z") byte z,
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
  private static double roundThreeDecimals(double d) {
    DecimalFormat twoDForm = new DecimalFormat("#.###");
    return Double.valueOf(twoDForm.format(d));
  }

  /**
   * TODO: remove this.
   * @return Static demo tile for rapid UI development only.
   */
  @GET
  @Path("{z}/{x}/{y}/demo.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] demo(
    @PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y, @Context HttpServletResponse response
  ) {

    // open the tiles to the world (especially your friendly localhost developer!)
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    VectorTileEncoder encoder = new VectorTileEncoder(256, 0, true);
    Coordinate[] coordinates = {
      new Coordinate(1, 1),
      new Coordinate(1, 254),
      new Coordinate(254, 254),
      new Coordinate(254, 1),
      new Coordinate(1, 1)
    };
    LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
    Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

    Map<String, String> meta = Maps.newHashMap();
    meta.put("id", z + "/" + x + "/" + y + "/" + 1);
    meta.put("1990", "0.1");
    meta.put("1991", "0.2");
    meta.put("1992", "0.3");
    meta.put("1993", "0.4");
    meta.put("1994", "0.5");
    meta.put("1995", "0.6");
    // y = mx + c
    meta.put("m", "0.001");
    meta.put("c", "-3425");
    meta.put("totalOccurrences", "234");
    meta.put("totalAllSpeciesInGroup", "453");

    encoder.addFeature("statistics", meta, poly);

    return encoder.encode();
  }
}
