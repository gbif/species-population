package org.gbif.population;


import org.gbif.population.data.DataDAO;
import org.gbif.population.data.DataService;
import org.gbif.population.resource.TileResource;

import io.dropwizard.Application;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.skife.jdbi.v2.DBI;

/**
 * The main entry point for running the member node.
 */
public class TileServerApplication extends Application<TileServerConfiguration> {

  private static final String APPLICATION_NAME = "Species Population Tile Server";

  public static void main(String[] args) throws Exception {
    new TileServerApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public final void initialize(Bootstrap<TileServerConfiguration> bootstrap) {
  }

  @Override
  public final void run(TileServerConfiguration configuration, Environment environment) {

    final DBIFactory factory = new DBIFactory();
    final DBI jdbi = factory.build(environment, configuration.getDataSourceFactory(), "MySQL");
    final DataDAO dataDao = jdbi.onDemand(DataDAO.class);
    final DataService dataService = new DataService(dataDao);
    environment.jersey().register(new TileResource(dataService));
  }
}
