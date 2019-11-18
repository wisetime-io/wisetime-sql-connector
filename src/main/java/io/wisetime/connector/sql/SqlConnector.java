/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.Subscribe;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import spark.Request;

import static io.wisetime.connector.sql.format.LogFormatter.format;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private final ConnectedDatabase database;
  private final TagQueryProvider tagQueryProvider;

  private SyncStore drainSyncStore;
  private SyncStore refreshSyncStore;

  private ConnectApi connectApi;
  private AtomicBoolean isPerformingUpdate = new AtomicBoolean();

  public SqlConnector(final ConnectedDatabase connectedDatabase, final TagQueryProvider tagQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    drainSyncStore = new SyncStore(connectorModule.getConnectorStore());
    refreshSyncStore = new SyncStore(connectorModule.getConnectorStore(), "refresh");
    connectApi = new ConnectApi(connectorModule.getApiClient());
  }

  @Override
  public String getConnectorType() {
    return "wisetime-sql-connector";
  }

  @Override
  public void performTagUpdate() {
    performTagUpdate(tagQueryProvider.getTagQueries());
  }

  private void performTagUpdate(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      isPerformingUpdate.set(false);
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingUpdate.compareAndSet(false, true)) {
      tagQueries.forEach(query -> {
        try {
          final Supplier<Boolean> allowSync = () -> !hasUpdatedQueries(tagQueries);

          // Drain everything
          syncAllNewRecords(query, allowSync);

          // By only resyncing one batch per scheduled performTagUpdate call, we implement a
          // slow resync mechanism that is separate from the main drain-everything mechanism.
          refreshOneBatch(query, allowSync);

        } finally {
          isPerformingUpdate.set(false);
        }
      });
      isPerformingUpdate.set(false);
    }
  }

  @Override
  public PostResult postTime(Request request, TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    return !tagQueryProvider.getTagQueries().isEmpty() && database.isAvailable();
  }

  @Override
  public void shutdown() {
    database.close();
    tagQueryProvider.stopWatching();
  }

  @Subscribe
  @Generated
  public void onTagQueriesChanged(List<TagQuery> tagQueries) {
    performTagUpdate(tagQueries);
  }

  @VisibleForTesting
  void syncAllNewRecords(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    LinkedList<TagSyncRecord> newTagSyncRecords;
    while (allowSync.get() && (newTagSyncRecords = getUnsyncedRecords(tagQuery, drainSyncStore)).size() > 0) {
      connectApi.upsertWiseTimeTags(newTagSyncRecords);
      drainSyncStore.markSyncPosition(tagQuery, newTagSyncRecords);
      log.info("New tag detection: " + format(newTagSyncRecords));
    }
  }

  @VisibleForTesting
  void refreshOneBatch(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    if (!allowSync.get()) {
      return;
    }
    final LinkedList<TagSyncRecord> refreshTagSyncRecords = getUnsyncedRecords(tagQuery, refreshSyncStore);
    if (refreshTagSyncRecords.isEmpty()) {
      // Next refresh batch to start again from the beginning
      log.info("Resetting tag refresh to start from the beginning");
      refreshSyncStore.resetSyncPosition(tagQuery);
      return;
    }
    connectApi.upsertWiseTimeTags(refreshTagSyncRecords);
    refreshSyncStore.markSyncPosition(tagQuery, refreshTagSyncRecords);
    log.info("Existing tag refresh: " + format(refreshTagSyncRecords));
  }

  @VisibleForTesting
  void setDrainSyncStore(final SyncStore syncStore) {
    this.drainSyncStore = syncStore;
  }

  @VisibleForTesting
  void setRefreshSyncStore(final SyncStore syncStore) {
    this.refreshSyncStore = syncStore;
  }

  @VisibleForTesting
  void setConnectApi(final ConnectApi connectApi) {
    this.connectApi = connectApi;
  }

  private LinkedList<TagSyncRecord> getUnsyncedRecords(final TagQuery query, final SyncStore syncStore) {
    final String syncMarker = syncStore.getSyncMarker(query);

    final List<String> idsToSkip = Stream
        .concat(query.getSkippedIds().stream(), syncStore.getLastSyncedIds(query).stream())
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());

    return database.getTagsToSync(query.getSql(), syncMarker, idsToSkip);
  }

  private boolean hasUpdatedQueries(final List<TagQuery> tagQueries) {
    return !tagQueries.equals(tagQueryProvider.getTagQueries());
  }
}
