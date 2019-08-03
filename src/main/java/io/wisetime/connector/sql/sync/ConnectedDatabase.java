/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Collection;
import java.util.List;
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

  public Collection<TagSyncRecord> getTagsToSync(final String sql, final String syncMarker,
      final List<String> lastSyncedReferences) {

    // TODO(SX)
    return ImmutableList.of();
  }

  public void close() {
    dataSource.close();
  }

  private Query query() {
    return fluentJdbc.query();
  }
}
