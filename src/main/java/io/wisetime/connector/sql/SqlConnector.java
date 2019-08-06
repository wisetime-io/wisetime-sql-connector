/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.format.LogFormatter.format;
import static io.wisetime.connector.sql.queries.TagQueryProvider.hasUniqueQueryNames;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import io.wisetime.generated.connect.TimeGroup;
import java.util.LinkedList;
import java.util.List;
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
  private ConnectApi connectApi;

  public SqlConnector(final ConnectedDatabase connectedDatabase, final TagQueryProvider tagQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    syncStore = new SyncStore(connectorModule.getConnectorStore());
    connectApi = new ConnectApi(connectorModule.getApiClient());
  }

  @Override
  public void performTagUpdate() {
    final List<TagQuery> tagQueries = tagQueryProvider.getQueries();
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      return;
    }

    // Important to ensure that we maintain correct sync for each query
    Preconditions.checkArgument(hasUniqueQueryNames(tagQueries), "Tag SQL query names must be unique");

    tagQueries.stream()
        .forEachOrdered(query -> {
          final String marker = syncStore.getSyncMarker(query.getName(), query.getInitialSyncMarker());
          final List<String> lastSyncedReferences = syncStore.getLastSyncedReferences(query.getName());
          LinkedList<TagSyncRecord> tagSyncRecords;

          while ((tagSyncRecords = database.getTagsToSync(query.getSql(), marker, lastSyncedReferences)).size() > 0) {
            connectApi.upsertWiseTimeTags(tagSyncRecords);
            syncStore.markSyncPosition(query.getName(), tagSyncRecords);
            log.info(format(tagSyncRecords));
          }
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

  @VisibleForTesting
  public void setSyncStore(final SyncStore syncStore) {
    this.syncStore = syncStore;
  }

  @VisibleForTesting
  public void setConnectApi(final ConnectApi connectApi) {
    this.connectApi = connectApi;
  }
}
