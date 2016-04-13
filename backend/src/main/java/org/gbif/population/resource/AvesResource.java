package org.gbif.population.resource;

import org.gbif.population.data.DataService;
import org.gbif.population.data.Features;
import org.gbif.population.data.PointFeature;
import org.gbif.population.data.ScientificName;
import org.gbif.population.utils.MercatorProjection;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import no.ecc.vectortile.VectorTileEncoder;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.codetome.hexameter.core.api.DefaultSatelliteData;
import org.codetome.hexameter.core.api.Hexagon;
import org.codetome.hexameter.core.api.HexagonOrientation;
import org.codetome.hexameter.core.api.HexagonalGrid;
import org.codetome.hexameter.core.api.HexagonalGridBuilder;
import org.codetome.hexameter.core.api.HexagonalGridLayout;
import org.codetome.hexameter.core.api.SatelliteData;
import org.codetome.hexameter.core.backport.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple resource that returns a demo tile.
 */
@Path("/aves")
@Singleton
public class AvesResource extends TileResource {
  private static final Logger LOG = LoggerFactory.getLogger(AvesResource.class);

  public AvesResource(DataService dataService) {
    super(dataService);
  }

  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/points.pbf")
  @Timed
  @Produces("application/x-protobuf")
  @Override
  public byte[] points(
    @PathParam("z") int z,
    @PathParam("x") int x,
    @PathParam("y") int y,
    @PathParam("speciesKey") int speciesKey,
    @QueryParam("minYear") int minYear,
    @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") int yearThreshold,
    @Context HttpServletResponse response
  ) throws IOException {
    return super.points(z, x, y, speciesKey, minYear, maxYear, yearThreshold, response);
  }

  @GET
  @Path("autocomplete")
  @Timed
  @Produces("application/json")  @Override
  public List<ScientificName> autocomplete(
    @QueryParam("prefix") String prefix, @Context HttpServletResponse response
  ) {
    return super.autocomplete(prefix, response);
  }

  @GET
  @Path("{speciesKey}/regression.json")
  @Timed
  @Produces("application/json")
  @Override
  public Map<String, Object> adhocRegression(
    @PathParam("speciesKey") int speciesKey,
    @QueryParam("minYear") int minYear,
    @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") int yearThreshold,
    @QueryParam("minLat") double minLatitude,
    @QueryParam("maxLat") double maxLatitude,
    @QueryParam("minLng") double minLongitude,
    @QueryParam("maxLng") double maxLongitude,
    @Context HttpServletResponse response
  ) throws IOException {
    return super.adhocRegression(speciesKey,
                                 minYear,
                                 maxYear,
                                 yearThreshold,
                                 minLatitude,
                                 maxLatitude,
                                 minLongitude,
                                 maxLongitude,
                                 response);
  }

  @GET
  @Path("{speciesKey}/{z}/{x}/{y}/hex.pbf")
  @Timed
  @Produces("application/x-protobuf")
  @Override
  public byte[] hex(
    @PathParam("z") int z,
    @PathParam("x") int x,
    @PathParam("y") int y,
    @PathParam("speciesKey") int speciesKey,
    @QueryParam("minYear") int minYear,
    @QueryParam("maxYear") int maxYear,
    @QueryParam("yearThreshold") int yearThreshold,
    @QueryParam("radius") Integer hexRadius,
    @Context HttpServletResponse response
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
      final double radius = hexRadius == null ? 80 : hexRadius * 16;
      final double hexWidth = radius * 2;
      final double hexHeight = Math.sqrt(3) * radius;

      final VectorTileEncoder encoder = new VectorTileEncoder(MercatorProjection.TILE_SIZE, 8, false);

      // World pixel addressing of the tile boundary, with 0,0 at top left
      int minTilePixelX = MercatorProjection.TILE_SIZE * x;
      int minTilePixelY = MercatorProjection.TILE_SIZE * y;

      // Set up the NxM grid of hexes, allowing for buffer of 2 hexagons all around.  If hexagons aligned perfectly
      // to the tile boundary a buffer of 1 would suffice.  However, a buffer of 2 allows us to move the grid to align
      // the hexagon polygons with the ones in the tile directly above and to the left.
      // The 3.0/2.5 factor is because we get 3 tiles in horizontal space of 2.5 widths due to the packing of hexagons
      int requiredWidth = (int) Math.ceil(MercatorProjection.TILE_SIZE * 3.0 / hexWidth * 2.5) + 4;
      int requiredHeight = (int) Math.ceil(MercatorProjection.TILE_SIZE / hexHeight) + 4;
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
      final double offsetX = (x * ((MercatorProjection.TILE_SIZE) % (1.5 * hexWidth))) % (1.5 * hexWidth);
      final double offsetY = (y * (MercatorProjection.TILE_SIZE % hexHeight)) % hexHeight;

      // for each feature returned from the DB locate its hexagon and store the data on the hexagon
      Set<Hexagon> dataCells = new ObjectOpenHashSet();
      Stopwatch timer = Stopwatch.createStarted();
      for (PointFeature feature : speciesFeatures) {
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

      Features.FeaturesReader groupFeatureReader = dataService.getGroupFeatures();
      LOG.info("Group lookup: {} msecs", timer.elapsed(TimeUnit.MILLISECONDS));
      timer.reset().start();

      int count = 0;
      while (groupFeatureReader.hasNext()) {
        addFeatureInHex((byte) z,
                        hexWidth,
                        hexHeight,
                        minTilePixelX,
                        minTilePixelY,
                        grid,
                        offsetX,
                        offsetY,
                        groupFeatureReader,
                        "groupCounts",
                        false, // only add group data to hexagons with species data (!)
                        (short)minYear,
                        (short)maxYear);
        count++;
      }
      LOG.info("Adding {} features for the group took {} msecs", count, timer.elapsed(TimeUnit.MILLISECONDS));

      LOG.info("{} hexagons with data", dataCells.size());
      timer.reset().start();
      for (Hexagon hexagon : dataCells) {

        // A hexagon is painted if it there is enough data.  We consider enough as having a user defined minimum number
        // of years with data for the species.
        SatelliteData data = hexagon.getSatelliteData().get(); // must exist
        Map<String, Integer> speciesCounts =
          data.<Map<String, Integer>>getCustomData("speciesCounts").get(); // must exist
        if (speciesCounts.size() >= yearThreshold) {
          Coordinate[] coordinates = new Coordinate[7];
          int i = 0;
          for (org.codetome.hexameter.core.api.Point point : hexagon.getPoints()) {
            coordinates[i++] = new Coordinate(point.getCoordinateX() - offsetX - (hexWidth * 1.5),
                                              point.getCoordinateY() - offsetY - (2 * hexHeight));
          }
          coordinates[6] = coordinates[0]; // close our polygon
          LinearRing linear = GEOMETRY_FACTORY.createLinearRing(coordinates);
          Polygon poly = new Polygon(linear, null, GEOMETRY_FACTORY);

          if (!data.<Map<Short, Integer>>getCustomData("groupCounts").isPresent()) {
            LOG.error("Skipping since no group data!!!");
            continue;
          }

          Map<Short, Integer> groupCounts = data.<Map<Short, Integer>>getCustomData("groupCounts").get(); // must exist
          Map<String, Object> meta = new Object2ObjectOpenHashMap();

          // convert hexagon centers to global pixel space, and find the lat,lng centers
          meta.put("id",
                   roundThreeDecimals(MercatorProjection.pixelYToLatitude(minTilePixelY + hexagon.getCenterY()
                                                                          - offsetY
                                                                          - (2 * hexHeight), (byte) z))
                   + "," +
                   roundThreeDecimals(MercatorProjection.pixelXToLongitude(minTilePixelX + hexagon.getCenterX()
                                                                           - offsetX
                                                                           - (1.5 * hexWidth), (byte) z))
          );
          // One cannot regress a single point.
          if (groupCounts.size() > 2) {
            SimpleRegression regression = new SimpleRegression();

            // use the group counts, since we wish to infer absence (0 count) for years where there are records within
            // the group but the species is not recorded for that year.
            for (Short yearAsShort : groupCounts.keySet()) {
              String year = Short.toString(yearAsShort);
              double speciesCount = speciesCounts.containsKey(year) ? (double) speciesCounts.get(year) : 0.0;
              double normalizedCount = speciesCount / groupCounts.get(yearAsShort);
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
          Map<String, Integer> groupCountCopy = Maps.newHashMap();
          for (Map.Entry<Short, Integer> e : groupCounts.entrySet()) {
            groupCountCopy.put(Short.toString(e.getKey()), e.getValue());
          }
          meta.put("groupCounts", MAPPER.writeValueAsString(groupCountCopy));
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
  private Hexagon addFeatureInHex(
    byte z,
    double hexWidth,
    double hexHeight,
    int minTilePixelX,
    int minTilePixelY,
    HexagonalGrid grid,
    double offsetX,
    double offsetY,
    Features.FeaturesReader feature,
    String satelliteDataKey,
    boolean canCreateSatelliteData,
    short minYear,
    short maxYear
  ) {
    double latLng[] = feature.readLocation();
    double pixelY = MercatorProjection.latitudeToPixelY(latLng[0], z);
    double pixelX = MercatorProjection.longitudeToPixelX(latLng[1], z);

    // always read the years, to ensure the buffer is read properly
    Map<Short, Integer> years = feature.readYears();
    short minYearInFeature = Short.MAX_VALUE;
    short maxYearInFeature = Short.MIN_VALUE;
    for (Short year : years.keySet()) {
      minYearInFeature = year < minYearInFeature ? year : minYearInFeature;
      maxYearInFeature = year > maxYearInFeature ? year : maxYearInFeature;
    }

    // trim to features that lie on the tile or within a 2-hexagon buffer, or outside the year range
    if (pixelX >= minTilePixelX - (1.5*hexWidth) && pixelX < minTilePixelX + MercatorProjection.TILE_SIZE + (1.5*hexWidth) &&
        pixelY >= minTilePixelY - (2*hexHeight) && pixelY < minTilePixelY + MercatorProjection.TILE_SIZE + (2*hexHeight) &&
        minYearInFeature >= minYear && maxYearInFeature <= maxYear
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
            cellData.addCustomData(satelliteDataKey, years);
          } else {
            Map<Short, Integer> existingCounts = (Map<Short, Integer>) cellData.getCustomData(satelliteDataKey).get();
            for (Map.Entry<Short, Integer> e : years.entrySet()) {
              int value = existingCounts.containsKey(e.getKey()) ?
                existingCounts.get(e.getKey()) + e.getValue() : e.getValue();
              existingCounts.put(e.getKey(), value);
            }
          }
        }
        return hexagon;
      } else {
        LOG.debug("skipping {},{} since no hexagon", latLng[0], latLng[1]);
      }
    } else {
      LOG.debug("skipping {},{}", latLng[0], latLng[1]);
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
