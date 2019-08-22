/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.format.LogFormatter.format;
import static io.wisetime.connector.sql.queries.TagQueryProvider.hasUniqueQueryNames;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import spark.Request;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private final ConnectedDatabase database;
  private final TagQueryProvider tagQueryProvider;
  private SyncStore syncStore;
  private ConnectApi connectApi;
  private AtomicBoolean isPerformingUpdate = new AtomicBoolean();

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
    performTagUpdate(tagQueryProvider.getQueries());
  }

  private void performTagUpdate(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      isPerformingUpdate.set(false);
      return;
    }
    // Important to ensure that we maintain correct sync for each query
    Preconditions.checkArgument(hasUniqueQueryNames(tagQueries), "Tag SQL query names must be unique");
    // locks to prevent concurrent access from scheduled update and event on script change
    if (isPerformingUpdate.compareAndSet(false, true)) {
      for (TagQuery query : tagQueries) {
        LinkedList<TagSyncRecord> tagSyncRecords;
        List<String> idsToSkip = getIdsToSkip(query);
        String syncMarker = syncStore.getSyncMarker(query);
        while (!hasUpdatedQueries(tagQueries)) { // run until queries change, must have break or endless loop
          tagSyncRecords = database.getTagsToSync(query.getSql(), syncMarker, idsToSkip); //get batch
          if (tagSyncRecords.size() > 0) {
            connectApi.upsertWiseTimeTags(tagSyncRecords);
            syncStore.markSyncPosition(query, tagSyncRecords);
            idsToSkip.addAll(//add ids to exclude from next batch
                tagSyncRecords.stream().map(TagSyncRecord::getId).collect(Collectors.toList())
            );
            log.info(format(tagSyncRecords));
          } else {
            // if syncMarker is not latest update and reset exclusion ids
            if (!syncMarker.equals(syncStore.getSyncMarker(query))) {
              syncMarker = syncStore.getSyncMarker(query);
              idsToSkip = getIdsToSkip(query);
            } else {
              break; //latest syncMarker and no more ids to exclude i.e all updated. break loop
            }
          }
        }
      }
      isPerformingUpdate.set(false); // unlock
    }
  }

  private List<String> getIdsToSkip(TagQuery query) {
    return Stream
        .concat(query.getSkippedIds().stream(), syncStore.getLastSyncedIds(query).stream())
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());
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

  @Subscribe
  @Generated //remove code block from jacoco check
  public void receiveTagQueriesChange(List<TagQuery> tagQueries) {
    performTagUpdate(tagQueries);
  }

  @VisibleForTesting
  void setSyncStore(final SyncStore syncStore) {
    this.syncStore = syncStore;
  }

  @VisibleForTesting
  void setConnectApi(final ConnectApi connectApi) {
    this.connectApi = connectApi;
  }

  private boolean hasUpdatedQueries(final List<TagQuery> tagQueries) {
    return !tagQueries.equals(tagQueryProvider.getQueries());
  }
}
