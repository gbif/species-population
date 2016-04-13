package org.gbif.population;

import org.gbif.population.data.AvesDAO;
import org.gbif.population.data.DataService;
import org.gbif.population.data.LepidopteraDAO;
import org.gbif.population.resource.AvesResource;
import org.gbif.population.resource.LepidopteraResource;

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
    final LepidopteraDAO lepidopteraDao = jdbi.onDemand(LepidopteraDAO.class);
    final AvesDAO avesDao = jdbi.onDemand(AvesDAO.class);

    final DataService lepidopteraService = new DataService(lepidopteraDao);
    final DataService avesService = new DataService(avesDao);
    environment.jersey().register(new LepidopteraResource(lepidopteraService));
    environment.jersey().register(new AvesResource(avesService));
  }
}
