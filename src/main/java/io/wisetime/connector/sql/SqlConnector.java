/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.generated.connect.TimeGroup;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  public SqlConnector(final ConnectedDatabase connectedDatabase) {
    database = connectedDatabase;
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    final Path tagSqlPath = Paths.get(
        RuntimeConfig.getString(SqlConnectorConfigKey.TAG_SQL_FILE)
            .orElseThrow(() -> new RuntimeException("Missing TAG_SQL_FILE configuration"))
    );
    tagQueryProvider = new TagQueryProvider(tagSqlPath);
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
