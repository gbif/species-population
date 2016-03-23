package org.gbif.population.resource;


import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import no.ecc.vectortile.VectorTileEncoder;
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

  public TileResource() {
  }

  @GET
  @Path("{z}/{x}/{y}.pbf")
  @Timed
  @Produces("application/x-protobuf")
  public byte[] tile(
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
