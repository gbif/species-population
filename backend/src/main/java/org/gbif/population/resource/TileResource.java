package org.gbif.population.resource;


import org.gbif.population.data.DataDAO;
import org.gbif.population.data.PointFeature;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import no.ecc.vectortile.VectorTileEncoder;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple resource that returns a demo tile.
 */
@Path("/")
@Singleton
public final class TileResource {

  private static final Logger LOG = LoggerFactory.getLogger(TileResource.class);

  private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
  private static final int TILE_SIZE = 256;
  private static final Pattern COLON = Pattern.compile(":");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final GlobalMercator MERCATOR = new GlobalMercator();
  private final DataDAO dataDao;


  public TileResource(DataDAO dataDao) {
    this.dataDao = dataDao;
  }

  @GET
  @Path("{speciesKey}/{z}/{x}/{y}.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] tile(
    @PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y, @PathParam("speciesKey") String speciesKey,
    @QueryParam("minYear") Integer minYear, @QueryParam("maxYear") Integer maxYear,
    @Context HttpServletResponse response
  ) throws IOException {

    // open the tiles to the world (especially your friendly localhost developer!)
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    int minYearAsInt = minYear == null ? 1900 : minYear;
    int maxYearAsInt = maxYear == null ? 2020 : maxYear; // if this runs longer than 2020, it'd be a miracle

    String json = dataDao.lookup(speciesKey, z, x, y);

    // cellID:{year:count}
    Map<String, Map<String,String>> features = Maps.newHashMap();
    if (json != null) {

      // TODO refactor this mess
      String groupJSON = dataDao.lookup("797", z, x, y);
      Map<String,Map<String,Integer>> groupYearLookup = MAPPER.readValue(groupJSON, Map.class);


      // year:{cellID:count}
      Map<String,Map<String,Integer>> years = MAPPER.readValue(json, Map.class);
      for (Map.Entry<String, Map<String,Integer>> year : years.entrySet() ) {
        int yearAsInt = Integer.parseInt(year.getKey());
        // only include the years of interest
        if (yearAsInt >= minYearAsInt && yearAsInt<=maxYearAsInt) {
          // {cellID:count}
          for (Map.Entry<String, Integer> cellCounts : year.getValue().entrySet()) {
            Map<String, String> yearCount = features.get(cellCounts.getKey());
            if (yearCount == null) {
              yearCount = Maps.newHashMap();
              // add a new feature keyed on the cellID
              features.put(cellCounts.getKey(), yearCount);
            }
            yearCount.put(year.getKey(),String.valueOf(cellCounts.getValue()));

            // now add the count for the group
            Integer groupCountForYearCell = groupYearLookup.get(year.getKey()).get(cellCounts.getKey());
            yearCount.put(year.getKey()+"_group", String.valueOf(groupCountForYearCell));
          }
        }
      }
    }

    VectorTileEncoder encoder = new VectorTileEncoder(TILE_SIZE, 0, true);
    int zoomAhead = 3; // defined by the processing
    int cellSize = TILE_SIZE / (1 << zoomAhead);
    int cellsPerTile = TILE_SIZE / cellSize;

    // cellID:{year:count}
    for (Map.Entry<String, Map<String,String>> feature : features.entrySet()) {

      int[] cell = parseCellID(feature.getKey()); // x,y

      // grids are always addressed from world 0,0 so remove the offsets to reference from 0,0 on the tile itself
      int x1 = cell[0] - x * cellsPerTile;
      int y1 = cell[1] - y * cellsPerTile;

      // now project them onto the grid instead of pixels
      int left = x1 * cellSize;
      int top = y1 * cellSize;

      Coordinate[] coordinates = {
        new Coordinate(left, top),
        new Coordinate(left + cellSize, top),
        new Coordinate(left + cellSize, top + cellSize),
        new Coordinate(left, top + cellSize),
        new Coordinate(left, top)
      };
      LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
      Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

      Map<String, Object> meta = Maps.newHashMap();
      meta.put("id", z + "/" + x + "/" + y + "/" + cell[0] + "/" + cell[1]);
      meta.putAll(feature.getValue());

      // TODO: refactor
      Map<String,String> points = feature.getValue();
      SimpleRegression regression = new SimpleRegression();
      LOG.debug("points {}", points);
      // require 3 points
      if (points.size()>6) {
        for (Map.Entry<String, String> e : points.entrySet()) {
          if (!e.getKey().endsWith("_group")) {
            int speciesValue = Integer.parseInt(e.getValue());
            int groupValue = Integer.parseInt(points.get(e.getKey() + "_group"));
            double normalizedCount = ((double) speciesValue) / groupValue;
            double yearAsDouble = Double.parseDouble(e.getKey());
            LOG.debug("Adding {}:{}", yearAsDouble, normalizedCount);
            regression.addData(yearAsDouble, normalizedCount);
          }
        }
      }
      meta.put("m", String.valueOf(regression.getSlope()));
      meta.put("m2", regression.getSlope());
      meta.put("c", String.valueOf(regression.getIntercept()));
      meta.put("significance", String.valueOf(regression.getSignificance()));
      meta.put("SSE", String.valueOf(regression.getSumSquaredErrors()));

      encoder.addFeature("statistics", meta, poly);

    }
    return encoder.encode();
  }

  private static int[] parseCellID(String cellID) {
    String[] atoms = COLON.split(cellID);
    return new int[]{Integer.parseInt(atoms[0]), Integer.parseInt(atoms[1])};
  }

  /**
   * Returns the data as points.
   * @return Static demo tile for rapid UI development only.
   */
  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/points.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] points(
    @PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y, @PathParam("speciesKey") String speciesKey,
    @QueryParam("minYear") Integer minYear, @QueryParam("maxYear") Integer maxYear,
    @QueryParam("yearThreshold") Integer yearThreshold, @Context HttpServletResponse response
  ) throws IOException {

    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    int minYearAsInt = minYear == null ? 1900 : minYear;
    int maxYearAsInt = maxYear == null ? 2020 : maxYear; // if this runs longer than 2020, it'd be a miracle
    int yearThresholdAsInt = yearThreshold == null ? 2 : yearThreshold; // sensible default - 2 points for regression


    List<PointFeature> features = dataDao.getRecords(speciesKey, minYearAsInt, maxYearAsInt, yearThresholdAsInt);
    LOG.debug("Found {} features", features.size());
    VectorTileEncoder encoder = new VectorTileEncoder(TILE_SIZE, 0, true);

    for (PointFeature feature : features) {
      // is the feature on the tile we are painting?
      int[] tileXY = MERCATOR.GoogleTile(feature.getLatitude(), feature.getLongitude(), z);
      if (tileXY[0] == x && tileXY[1] == y) {

        // find the pixel offsets local to the top left of the tile
        int[] tileLocalXY = getTileLocalCoordinates(z, feature);

        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(tileLocalXY[0], tileLocalXY[1]));
        Map<String, Object> meta = Maps.newHashMap();

        SimpleRegression regression = new SimpleRegression();
        // require 2 points minimum
        if (feature.getSpeciesCounts().size()>2) {
          for (Integer year : feature.getSpeciesCounts().keySet()) {
            double normalizedCount = ((double) feature.getSpeciesCounts().get(year)) /
                                     feature.getGroupCounts().get(year);
              regression.addData(year, normalizedCount);
          }
        }
        meta.put("slope", regression.getSlope());
        meta.put("intercept", regression.getIntercept());
        meta.put("significance", regression.getSignificance());
        meta.put("SSE", regression.getSumSquaredErrors());
        meta.put("interceptStdErr", regression.getInterceptStdErr());
        meta.put("meanSquareError", regression.getMeanSquareError());
        meta.put("slopeStdErr", regression.getSlopeStdErr());
        meta.put("speciesCounts", feature.getSpeciesCounts());
        meta.put("groupCounts", feature.getGroupCounts());
        encoder.addFeature("points", meta, point);

      } else {
        LOG.debug("{},{} falls on {},{} at zoom", feature.getLatitude(), feature.getLongitude(), tileXY[0],tileXY[1], z);
      }
    }
    return encoder.encode();
  }

  private int[] getTileLocalCoordinates(int z, PointFeature feature) {
    double[] metersXY = MERCATOR.LatLonToMeters(feature.getLatitude(), feature.getLongitude());
    LOG.debug("{},{} as meters {},{}", feature.getLatitude(), feature.getLongitude(), metersXY[0], metersXY[1]);
    int[] pixelsXY = MERCATOR.MetersToPixels(metersXY[0], metersXY[1], z);
    LOG.debug("{},{} as pixels {},{}", feature.getLatitude(), feature.getLongitude(), pixelsXY[0], pixelsXY[1]);
    int[] rasterXY = MERCATOR.PixelsToRaster(pixelsXY[0], pixelsXY[1], z);
    LOG.debug("{},{} as raster {},{}", feature.getLatitude(), feature.getLongitude(), rasterXY[0], rasterXY[1]);
    int[] tileLocalXY = MERCATOR.PixelsToTileLocal(rasterXY[0], rasterXY[1]);
    LOG.debug("{},{} on tile local {},{}", feature.getLatitude(), feature.getLongitude(), tileLocalXY[0], tileLocalXY[1]);
    return tileLocalXY;
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

    VectorTileEncoder encoder = new VectorTileEncoder(TILE_SIZE, 0, true);
    Coordinate[] coordinates = {
      new Coordinate(64, 64),
      new Coordinate(64, 192),
      new Coordinate(192, 192),
      new Coordinate(192, 64),
      new Coordinate(64, 64)
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
