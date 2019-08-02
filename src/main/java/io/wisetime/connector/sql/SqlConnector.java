/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.inject.Inject;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.generated.connect.TimeGroup;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import spark.Request;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  @Inject
  TagQueryProvider tagQueryProvider;

  private ApiClient apiClient;
  private ConnectorStore connectorStore;

  @Override
  public void init(ConnectorModule connectorModule) {
    apiClient = connectorModule.getApiClient();
    connectorStore = connectorModule.getConnectorStore();
  }

  @Override
  public void performTagUpdate() {
    final List<TagQuery> queries = tagQueryProvider.getQueries();
    // TODO(SX)
  }

  @Override
  public PostResult postTime(Request request, TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    // TODO(SX): Check whether we can query the database
    return false;
  }

  @Override
  public void shutdown() {
    tagQueryProvider.stopWatching();
    // TODO(SX): Shutdown database connection
  }
}
