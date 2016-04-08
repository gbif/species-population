package org.gbif.population.resource;


import org.gbif.population.data.DataDAO;
import org.gbif.population.data.DataService;
import org.gbif.population.data.PointFeature;
import org.gbif.population.data.ScientificName;
import org.gbif.population.data.YearFeature;

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
  private static final int TILE_SIZE = 256;
  private static final int LARGE_TILE_SIZE = 4096; // big vector tiles
  private static final int TILE_RATIO = LARGE_TILE_SIZE / TILE_SIZE; // must be exact fit (!)
  private static final Pattern COMMA = Pattern.compile(",");
  private static final Pattern COLON = Pattern.compile(":");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final GlobalMercator MERCATOR = new GlobalMercator();
  private final DataService dataService;

  //private Map<Point2D, Map<String, Integer>> groupFeatures;


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

      VectorTileEncoder encoder = new VectorTileEncoder(TILE_SIZE, 0, false);

      for (PointFeature feature : speciesFeatures) {

        MercatorProjection.

        // is the feature on the tile we are painting?
        java.awt.Point tileAddress = MercatorProjectionUtils.toTileXY(feature.getLatitude(), feature.getLongitude(), z);
        if (tileAddress.getX() == x && tileAddress.getY() == y) {

          // find the pixel offsets local to the top left of the tile
          int[] tileLocalXY = getTileLocalCoordinates(z, feature);

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
    @PathParam("speciesKey") String speciesKey, @QueryParam("minYear") int minYear, @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") int yearThreshold, @QueryParam("minLat") double minLatitude, @QueryParam("maxLat") double maxLatitude,
    @QueryParam("minLng") double minLongitude, @QueryParam("maxLng") double maxLongitude, @Context HttpServletResponse response
  ) throws IOException {
    // open the tiles to the world
    response.addHeader("Allow-Control-Allow-Methods", "GET,OPTIONS");
    response.addHeader("Access-Control-Allow-Origin", "*");

    List<YearFeature> features = null; //dataDao.getRecords(speciesKey, minYear, maxYear, yearThreshold,
                                         //           minLatitude, maxLatitude, minLongitude, maxLongitude);
    SimpleRegression regression = new SimpleRegression();
    Map<String, Integer> speciesCounts = Maps.newHashMap();
    Map<String, Integer> groupCounts = Maps.newHashMap();
    LOG.info("Found {} years with data", features.size());
    for (YearFeature feature : features) {
      regression.addData((double)feature.getYear(), ((double)feature.getSpeciesCount()) / feature.getGroupCount());
      speciesCounts.put(String.valueOf(feature.getYear()), feature.getSpeciesCount());
      groupCounts.put(String.valueOf(feature.getYear()), feature.getGroupCount());
    }
    Map<String, Object> data = Maps.newHashMap();
    data.put("slope", regression.getSlope());
    data.put("intercept", regression.getIntercept());
    data.put("significance", regression.getSignificance());
    data.put("SSE", regression.getSumSquaredErrors());
    data.put("interceptStdErr", regression.getInterceptStdErr());
    data.put("meanSquareError", regression.getMeanSquareError());
    data.put("slopeStdErr", regression.getSlopeStdErr());
    data.put("speciesCounts", speciesCounts);
    data.put("groupCounts", groupCounts);
    LOG.info("Result {}", data);
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

      final VectorTileEncoder encoder = new VectorTileEncoder(LARGE_TILE_SIZE, 8, false);

      // set up the NxM grid of hexes, allowing for 1 cells buffer
      // the 6.0/5.0 factor is because we get 6 tiles in horizontal space of 5 widths due to the packing of hexagons
      int requiredWidth = (int)Math.ceil(LARGE_TILE_SIZE * 6.0 / hexWidth * 5.0) + 2;
      int requiredHeight = (int)Math.ceil(LARGE_TILE_SIZE / hexHeight) + 2;
      LOG.debug("Hex sizes {}x{} calculated grid {}x{}", hexWidth, hexHeight, requiredWidth, requiredHeight);

      HexagonalGridBuilder builder = new HexagonalGridBuilder()
        .setGridWidth(requiredWidth)
        .setGridHeight(requiredHeight)
        .setGridLayout(HexagonalGridLayout.RECTANGULAR)
        .setOrientation(HexagonOrientation.FLAT_TOP)
        .setRadius(radius);

      HexagonalGrid grid = builder.build();

      // hexagons do not align at boundaries, and therefore we need to determine the offsets to ensure polygons
      // meet correctly across tiles.
      // The maximum offset is 1.5 cells horizontally and 1 cell vertically due to using flat top tiles.  This is
      // apparent when you see a picture. See this as an excellent resource
      // http://www.redblobgames.com/grids/hexagons/#basics
      final double offsetX = (x*((LARGE_TILE_SIZE)%(1.5*hexWidth)))%(1.5*hexWidth);
      final double offsetY = (y*(LARGE_TILE_SIZE%hexHeight))%hexHeight;

      // for each feature returned from the DB locate its hexagon and store the data on the hexagon
      Set<Hexagon> dataCells = Sets.newHashSet();
      for(PointFeature feature : speciesFeatures) {

        // is the feature on the tile we are painting? TODO: we need to consider buffer!!!
        java.awt.Point tileAddress = MercatorProjectionUtils.toTileXY(feature.getLatitude(), feature.getLongitude(), z);
        if (tileAddress.getX() == x && tileAddress.getY() == y) {

          // find the pixel offsets local to the top left of the tile
          int[] tileLocalXYSmallTile = getTileLocalCoordinates(z, feature);
          double[] tileLocalXY = new double[]{
            tileLocalXYSmallTile[0] * TILE_RATIO,
            tileLocalXYSmallTile[1] * TILE_RATIO
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

            // merge in the species data
            if (!cellData.getCustomData("speciesCounts").isPresent()) {
              cellData.addCustomData("speciesCounts", feature.getYearCounts());
            } else {
              Map<String, Integer> existingCounts = (Map<String, Integer>) cellData.getCustomData("speciesCounts").get();
              for (Map.Entry<String, Integer> e : feature.getYearCounts().entrySet()) {
                int value = existingCounts.containsKey(e.getKey()) ?
                  existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
                existingCounts.put(e.getKey(), value);
              }
            }
            dataCells.add(hexagon);
          }
        }
      }

      List<PointFeature> groupFeatures  = dataService.getGroupFeatures(minYear, maxYear);
      for(PointFeature feature : groupFeatures) {

        // is the feature on the tile we are painting? TODO: we need to consider buffer!!!
        java.awt.Point tileAddress = MercatorProjectionUtils.toTileXY(feature.getLatitude(), feature.getLongitude(), z);
        if (tileAddress.getX() == x && tileAddress.getY() == y) {

          // find the pixel offsets local to the top left of the tile
          int[] tileLocalXYSmallTile = getTileLocalCoordinates(z, feature);
          double[] tileLocalXY = new double[]{
            tileLocalXYSmallTile[0] * TILE_RATIO,
            tileLocalXYSmallTile[1] * TILE_RATIO
          };

          // We need to consider that the hexagons will be offset at rendering time to adjust for tile boundaries.
          Optional<Hexagon> hex = grid.getByPixelCoordinate(tileLocalXY[0] + offsetX, tileLocalXY[1] + offsetY);
          if (hex.isPresent()) {
            Hexagon hexagon = hex.get();

            // satellite data must be present if the species is in the hexagon
            if (hexagon.getSatelliteData().isPresent()) {
              SatelliteData cellData = hexagon.getSatelliteData().get();

              if (!cellData.getCustomData("groupCounts").isPresent()) {
                cellData.addCustomData("groupCounts", feature.getYearCounts());
              } else {
                Map<String, Integer> existingCounts = (Map<String, Integer>) cellData.getCustomData("groupCounts").get();
                for (Map.Entry<String, Integer> e : feature.getYearCounts().entrySet()) {
                  int value = existingCounts.containsKey(e.getKey()) ?
                    existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
                  existingCounts.put(e.getKey(), value);
                }
              }
            }
          }
        }
      }

      // all hexagons are in the dataCells, which may or may not have group data.

      for (Hexagon hexagon : dataCells) {
        Coordinate[] coordinates = new Coordinate[7];
        int i=0;
        for(org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {
          coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX, point.getCoordinateY() - offsetY);
        }
        coordinates[6] = coordinates[0]; // close our polygon
        LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

        SatelliteData data = hexagon.getSatelliteData().get(); // must exist
        Map<String,Integer> groupCounts = data.<Map<String,Integer>>getCustomData("groupCounts").get();

        Map<String, Object> meta = Maps.newHashMap();
        double[] latLng = latLngCentreOf(hexagon,offsetX, offsetY, z,x,y);
        meta.put("id", roundTwoDecimals(latLng[0]) + "," + roundTwoDecimals(latLng[1]));
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
    LOG.debug("latLng: {},{}", roundTwoDecimals(-1 * latLng[0]), roundTwoDecimals(latLng[1]));
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
    //return tileLocalXY;

    int x= MercatorProjectionUtils.getOffsetX(feature.getLatitude(), feature.getLongitude(), z);
    int y= MercatorProjectionUtils.getOffsetY(feature.getLatitude(), feature.getLongitude(), z);
    return new int[] {x,y};
  }

  public static Point2D toNormalisedPixelCoords(double lat, double lng) {
    lng = (lng%360) / 360; // support the wrap around libraries
    lat = 0.5 - ((Math.log(Math.tan((Math.PI / 4) + ((0.5 * Math.PI * lat) / 180))) / Math.PI) / 2.0);
    return new Point2D.Double(lng, lat);
  }


  public static int toTileX(double lng, int zoom) {
    lng = (lng%360) / 360; // support the wrap around libraries
    int scale = 1 << zoom;
    return (int)(lng * scale);
  }

  public static int toTileY(double lat, int zoom) {
    lat = 0.5 - ((Math.log(Math.tan((Math.PI / 4) + ((0.5 * Math.PI * lat) / 180))) / Math.PI) / 2.0);
    int scale = 1 << zoom;
    return (int)(lat * scale);
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
