package org.gbif.population.resource;

import org.gbif.population.data.DataService;
import org.gbif.population.data.ScientificName;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.codahale.metrics.annotation.Timed;

/**
 * A simple resource that returns a demo tile.
 */
@Path("/lepidoptera")
@Singleton
public class LepidopteraResource extends TileResource {

  public LepidopteraResource(DataService dataService) {
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
    return super.hex(z, x, y, speciesKey, minYear, maxYear, yearThreshold, hexRadius, response);
  }
}
