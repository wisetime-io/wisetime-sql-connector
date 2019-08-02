/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.zaxxer.hikari.HikariDataSource;
import org.codejargon.fluentjdbc.api.FluentJdbc;
import org.codejargon.fluentjdbc.api.FluentJdbcBuilder;
import org.codejargon.fluentjdbc.api.mapper.Mappers;
import org.codejargon.fluentjdbc.api.query.Query;

/**
 * The SQL database that the connector integrates with.
 *
 * @author shane.xie
 */
public class ConnectedDatabase {

  private HikariDataSource dataSource;
  private final FluentJdbc fluentJdbc;

  public ConnectedDatabase(final HikariDataSource dataSource) {
    this.dataSource = dataSource;
    this.fluentJdbc = new FluentJdbcBuilder().connectionProvider(dataSource).build();
  }

  public boolean isAvailable() {
    try {
      query().select("SELECT 1").firstResult(Mappers.singleInteger());
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  public void close() {
    dataSource.close();
  }

  private Query query() {
    return fluentJdbc.query();
  }
}
