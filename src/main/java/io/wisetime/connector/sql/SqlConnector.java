/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.generated.connect.TimeGroup;
import lombok.extern.slf4j.Slf4j;
import spark.Request;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private ConnectedDatabase database;
  private TagQueryProvider tagQueryProvider;
  private SyncStore syncStore;
  private ApiClient apiClient;

  public SqlConnector(final ConnectedDatabase connectedDatabase, final TagQueryProvider tagQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    syncStore = new SyncStore(connectorModule.getConnectorStore());
    apiClient = connectorModule.getApiClient();
  }

  @Override
  public void performTagUpdate() {
    tagQueryProvider.getQueries().stream()
        .forEachOrdered(tagQuery -> {
          // TODO(SX)
        });
  }

  @Override
  public PostResult postTime(Request request, TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    return !tagQueryProvider.getQueries().isEmpty() && database.isAvailable();
  }

  @Override
  public void shutdown() {
    database.close();
    tagQueryProvider.stopWatching();
  }
}
