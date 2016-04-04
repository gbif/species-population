package org.gbif.population.resource;


import org.gbif.population.data.DataDAO;
import org.gbif.population.data.PointFeature;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
  private static final int TILE_SIZE = 256;
  private static final int LARGE_TILE_SIZE = 4096; // big vector tiles
  private static final int TILE_RATIO = LARGE_TILE_SIZE / TILE_SIZE; // must be exact fit (!)
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

  /**
   * Returns the data as hex grids.
   */
  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/hex.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] hex(
    @PathParam("z") final int z, @PathParam("x") final int x, @PathParam("y") final int y, @PathParam("speciesKey") String speciesKey,
    @QueryParam("minYear") Integer minYear, @QueryParam("maxYear") Integer maxYear,
    @QueryParam("yearThreshold") Integer yearThreshold, @QueryParam("radius") Integer hexRadius, @Context HttpServletResponse response
  ) throws IOException {

    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    int minYearAsInt = minYear == null ? 1900 : minYear;
    int maxYearAsInt = maxYear == null ? 2020 : maxYear; // if this runs longer than 2020, it'd be a miracle
    int yearThresholdAsInt = yearThreshold == null ? 2 : yearThreshold; // sensible default - 2 points for regression
    List<PointFeature> features = dataDao.getRecords(speciesKey, minYearAsInt, maxYearAsInt, yearThresholdAsInt);
    LOG.debug("Found {} features", features.size());

    // 16x decrease due to painting on large vector tiles.  Picks a sensible default
    final double radius = hexRadius == null ? 80 : hexRadius*16;
    final double hexWidth = radius*2;
    final double hexHeight = Math.sqrt(3) * radius;

    // Buffer needs to be 3xradius
    final VectorTileEncoder encoder = new VectorTileEncoder(LARGE_TILE_SIZE, (int)Math.ceil(Math.max(hexWidth,hexHeight)), false);

    // set up the NxM grid of hexes, allowing for 2 cells buffer horizonatally and 1 vertically (since flat topped)
    // the 6.0/5.0 factor is because we get 6 tiles in horizontal space of 5 widths due to the packing
    int requiredWidth = (int)Math.ceil(LARGE_TILE_SIZE * 6.0 / hexWidth * 5.0) + 4;
    int requiredHeight = (int)Math.ceil(LARGE_TILE_SIZE / hexHeight) + 2;
    LOG.debug("Hex sizes {}x{} calculated grid {}x{}", hexWidth, hexHeight, requiredWidth, requiredHeight);

    HexagonalGridBuilder builder = new HexagonalGridBuilder()
      .setGridWidth(requiredWidth)
      .setGridHeight(requiredHeight)
      .setGridLayout(HexagonalGridLayout.RECTANGULAR)
      .setOrientation(HexagonOrientation.FLAT_TOP)
      .setRadius(radius);

    HexagonalGrid grid = builder.build();
    Observable<Hexagon> hexagons = grid.getHexagons();

    // hexagons do not align at boundaries, and therefore we need to determine the offsets to ensure polygons
    // meet correctly across tiles.
    // The maximum offset is 1.5 cells horizontally and 1 cell vertically due to using flat top tiles.  This is
    // apparent when you see a picture. See this as an excellent resource
    // http://www.redblobgames.com/grids/hexagons/#basics
    final double offsetX = (x*((LARGE_TILE_SIZE)%(1.5*hexWidth)))%(1.5*hexWidth);
    final double offsetY = (y*(LARGE_TILE_SIZE%hexHeight))%hexHeight;

    // for each feature returned from the DB locate its hexagon and store the data on the hexagon
    Set<Hexagon> dataCells = Sets.newHashSet();
    for(PointFeature feature : features) {

      // is the feature on the tile we are painting? TODO: we need to consider buffer!!!
      int[] tileXY = MERCATOR.GoogleTile(feature.getLatitude(), feature.getLongitude(), z);
      if (tileXY[0] == x && tileXY[1] == y) {

        // find the pixel offsets local to the top left of the tile
        int[] tileLocalXYSmallTile = getTileLocalCoordinates(z, feature);
        double[] tileLocalXY = new double[]{
          tileLocalXYSmallTile[0] * LARGE_TILE_SIZE/TILE_SIZE,
          tileLocalXYSmallTile[1] * LARGE_TILE_SIZE/TILE_SIZE
        };

        // We need to consider that the hexagons will be offset at rendering time to adjust for tile boundaries.
        Optional<Hexagon> hex = grid.getByPixelCoordinate(tileLocalXY[0] + offsetX, tileLocalXY[1] + offsetY);
        if (hex.isPresent()) {
          Hexagon hexagon = hex.get();


          // store the data on the cell
          SatelliteData cellData = null;
          Optional<SatelliteData> cellDataO = hexagon.getSatelliteData();
          if (!hexagon.getSatelliteData().isPresent()) {
            cellData = new DefaultSatelliteData();
            hexagon.setSatelliteData(cellData);
          } else {
            cellData = hexagon.getSatelliteData().get();
          }
          // ID the hexagon by the latLng of the center to avoid having to faff around with tile boundaries
          double[] latLng = latLngCentreOf(hexagon,offsetX, offsetY, z,x,y);
          cellData.addCustomData("id", roundTwoDecimals(latLng[0]) + "," + roundTwoDecimals(latLng[1]));
          if (!cellData.getCustomData("speciesCounts").isPresent()) {
            cellData.addCustomData("speciesCounts", feature.getSpeciesCounts());
          } else {
            Map<Integer,Integer> existingCounts = (Map<Integer,Integer>)cellData.getCustomData("speciesCounts").get();
            for (Map.Entry<Integer, Integer> e : feature.getSpeciesCounts().entrySet()) {
              int value = existingCounts.containsKey(e.getKey()) ?
                existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
              existingCounts.put(e.getKey(), value);
            }
          }
          if (!cellData.getCustomData("groupCounts").isPresent()) {
            cellData.addCustomData("groupCounts", feature.getGroupCounts());
          } else {
            Map<Integer,Integer> existingCounts = (Map<Integer,Integer>)cellData.getCustomData("groupCounts").get();
            for (Map.Entry<Integer, Integer> e : feature.getGroupCounts().entrySet()) {
              int value = existingCounts.containsKey(e.getKey()) ?
                existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
              existingCounts.put(e.getKey(), value);
            }
          }
          dataCells.add(hex.get());
        }
      }
    }



    for (Hexagon hexagon : dataCells) {
      Coordinate[] coordinates = new Coordinate[7];
      int i=0;
      for(org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {
        coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX, point.getCoordinateY() - offsetY);
      }
      coordinates[6] = coordinates[0]; // close our polygon
      LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
      Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

      Map<String, Object> meta = Maps.newHashMap();
      SatelliteData data = hexagon.getSatelliteData().get(); // must exist

      Map<Integer,Integer> speciesCounts = (Map<Integer,Integer>)data.getCustomData("speciesCounts").get();
      Map<Integer,Integer> groupCounts = (Map<Integer,Integer>)data.getCustomData("groupCounts").get();
      SimpleRegression regression = new SimpleRegression();
      // require 2 points minimum
      if (speciesCounts.size()>2) {
        for (Integer year : speciesCounts.keySet()) {
          double normalizedCount = ((double) speciesCounts.get(year)) /
                                   groupCounts.get(year);
          regression.addData(year, normalizedCount);
        }
      }
      meta.put("id", data.getCustomData("id"));
      meta.put("slope", regression.getSlope());
      meta.put("intercept", regression.getIntercept());
      meta.put("significance", regression.getSignificance());
      meta.put("SSE", regression.getSumSquaredErrors());
      meta.put("interceptStdErr", regression.getInterceptStdErr());
      meta.put("meanSquareError", regression.getMeanSquareError());
      meta.put("slopeStdErr", regression.getSlopeStdErr());
      meta.put("speciesCounts", speciesCounts);
      meta.put("groupCounts", groupCounts);
      encoder.addFeature("hex", meta, poly);
    }

    // for each of our hexagons, create a feature
    /*
    grid.getHexagons().forEach(new Action1<Hexagon>() {
      @Override
      public void call(Hexagon hexagon) {
        Coordinate[] coordinates = new Coordinate[7];
        int i=0;
        for(org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {

          coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX, point.getCoordinateY() - offsetY);
        }
        coordinates[6] = coordinates[0]; // close our polygon

        LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);
        Map<String, Object> meta = Maps.newHashMap();

        // ID the hexagon by the latLng of the center to avoid having to faff around with tile boundaries
        double[] latLng = latLngCentreOf(hexagon,offsetX, offsetY, z,x,y);
        meta.put("id", latLng[0] + "," + latLng[1]);
        encoder.addFeature("hex", meta, poly);
      }
    });
    */

    return encoder.encode();
  }

  /**
   * Returns the lat,lng in WGS84 space of the hexagon which is on the tile at z,x,y address.
   */
  private double[] latLngCentreOf(Hexagon hexagon, double offsetX, double offsetY, int z, int x, int y) {
    // convert to small tile addresses for the mercator utils
    int centerX = (int)(hexagon.getCenterX() / TILE_RATIO);
    int centerY = (int)(hexagon.getCenterY() / TILE_RATIO);

    LOG.debug("centers: {},{}", centerX, centerY);
    int[] coords = MERCATOR.TileLocalPixelsToGlobal(centerX, centerY, x, y);
    // compensate for the offset for where the hexagon was drawn
    coords[0] = coords[0]- (int)(offsetX/TILE_RATIO);
    coords[1] = coords[1]- (int)(offsetY/TILE_RATIO);
    LOG.debug("coords: {},{}", coords[0], coords[1]);
    double[] meters = MERCATOR.PixelsToMeters(coords[0], coords[1], z);
    LOG.debug("meters: {},{}", meters[0], meters[1]);
    double[]latLng = MERCATOR.MetersToLatLon(meters[0], meters[1]);
    LOG.info("latLng: {},{}", roundTwoDecimals(-1 * latLng[0]), roundTwoDecimals(latLng[1]));
    return new double[]{-1 * latLng[0], latLng[1]};
  }

  /**
   * Rounds to 2 decimal places
   */
  private static double roundTwoDecimals(double d) {
    DecimalFormat twoDForm = new DecimalFormat("#.##");
    return Double.valueOf(twoDForm.format(d));
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
