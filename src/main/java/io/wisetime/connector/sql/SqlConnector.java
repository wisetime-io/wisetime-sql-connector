/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.format.LogFormatter.formatTags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.queries.QueryProvider;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import io.wisetime.connector.sql.sync.TagSyncStore;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeSyncService;
import io.wisetime.connector.sql.sync.activity_type.hash.ActivityTypeSyncWithHashService;
import io.wisetime.connector.sql.sync.activity_type.marker.ActivityTypeSyncWithMarkerService;
import io.wisetime.generated.connect.TimeGroup;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import spark.Request;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private final ConnectedDatabase database;
  private final QueryProvider<TagQuery> tagQueryProvider;
  private final QueryProvider<ActivityTypeQuery> activityTypeQueryProvider;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TagSyncStore tagDrainSyncStore;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TagSyncStore tagRefreshSyncStore;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncService activityTypeSyncWithHashService;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncService activityTypeSyncWithMarkerService;

  private ConnectApi connectApi;
  private final AtomicBoolean isPerformingTagUpdate = new AtomicBoolean();
  private final AtomicBoolean isPerformingTagSlowResync = new AtomicBoolean();
  private final AtomicBoolean isPerformingActivityTypeSync = new AtomicBoolean();
  private final AtomicBoolean isPerformingActivityTypeSlowSync = new AtomicBoolean();

  public SqlConnector(final ConnectedDatabase connectedDatabase,
      final QueryProvider<TagQuery> tagQueryProvider, final QueryProvider<ActivityTypeQuery> activityTypeQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
    this.tagQueryProvider.setListener(this::performTagUpdate);
    this.activityTypeQueryProvider = activityTypeQueryProvider;
    this.activityTypeQueryProvider.setListener(this::performActivityTypeUpdate);
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    tagDrainSyncStore = new TagSyncStore(connectorModule.getConnectorStore());
    tagRefreshSyncStore = new TagSyncStore(connectorModule.getConnectorStore(), "refresh");
    connectApi = new ConnectApi(connectorModule.getApiClient());
    activityTypeSyncWithHashService =
        new ActivityTypeSyncWithHashService(connectorModule.getConnectorStore(), connectApi, database);
    activityTypeSyncWithMarkerService =
        new ActivityTypeSyncWithMarkerService(connectorModule.getConnectorStore(), connectApi, database);
  }

  @Override
  public String getConnectorType() {
    return "wisetime-sql-connector";
  }

  @Override
  public void performTagUpdate() {
    performTagUpdate(tagQueryProvider.getQueries());
  }

  private void performTagUpdate(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTagUpdate.compareAndSet(false, true)) {
      try {
        tagQueries.forEach(query -> {
          final Supplier<Boolean> allowSync = () -> !hasUpdatedQueries(tagQueries);
          // Drain everything
          syncAllNewRecords(query, allowSync);
        });
      } finally {
        isPerformingTagUpdate.set(false);
      }
    }
  }

  @Override
  public void performTagUpdateSlowLoop() {
    performSlowResync(tagQueryProvider.getQueries());
  }

  private void performSlowResync(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTagSlowResync.compareAndSet(false, true)) {
      try {
        tagQueries.forEach(query -> {
          final Supplier<Boolean> allowSync = () -> !hasUpdatedQueries(tagQueries);
          // slow resync mechanism that is separate from the main drain-everything mechanism.
          refreshOneBatch(query, allowSync);
        });
      } finally {
        isPerformingTagSlowResync.set(false);
      }
    }
  }

  @Override
  public void performActivityTypeUpdate() {
    performActivityTypeUpdate(activityTypeQueryProvider.getQueries());
  }

  private void performActivityTypeUpdate(List<ActivityTypeQuery> activityTypeQueries) {
    if (activityTypeQueries.isEmpty()) {
      log.warn("No activity type SQL queries configured. Skipping activity types sync.");
      return;
    }
    Preconditions.checkArgument(activityTypeQueries.size() == 1, "At most one activity type SQL query must be provided");
    final ActivityTypeQuery query = activityTypeQueries.get(0);

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingActivityTypeSync.compareAndSet(false, true)) {
      try {
        getActivityTypeSyncService(query)
            .performActivityTypeUpdate(query);
      } finally {
        isPerformingActivityTypeSync.set(false);
      }
    }
  }

  @Override
  public void performActivityTypeUpdateSlowLoop() {
    performActivityTypeUpdateSlowLoop(activityTypeQueryProvider.getQueries());
  }

  private void performActivityTypeUpdateSlowLoop(List<ActivityTypeQuery> activityTypeQueries) {
    if (activityTypeQueries.isEmpty()) {
      log.warn("No activity type SQL queries configured. Skipping activity types slow loop sync.");
      return;
    }
    Preconditions.checkArgument(activityTypeQueries.size() == 1, "At most one activity type SQL query must be provided");
    final ActivityTypeQuery query = activityTypeQueries.get(0);

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingActivityTypeSlowSync.compareAndSet(false, true)) {
      try {
        getActivityTypeSyncService(query)
            .performActivityTypeUpdateSlowLoop(query);
      } finally {
        isPerformingActivityTypeSlowSync.set(false);
      }
    }
  }

  private ActivityTypeSyncService getActivityTypeSyncService(ActivityTypeQuery query) {
    return query.hasSyncMarker() ? activityTypeSyncWithMarkerService : activityTypeSyncWithHashService;
  }

  @Override
  public PostResult postTime(Request request, TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    return tagQueryProvider.isHealthy() && activityTypeQueryProvider.isHealthy() && database.isAvailable();
  }

  @Override
  public void shutdown() {
    database.close();
    tagQueryProvider.stop();
  }

  @VisibleForTesting
  void syncAllNewRecords(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    LinkedList<TagSyncRecord> newTagSyncRecords;
    while (allowSync.get() && (newTagSyncRecords = getUnsyncedRecords(tagQuery, tagDrainSyncStore)).size() > 0) {
      connectApi.upsertWiseTimeTags(newTagSyncRecords);
      tagDrainSyncStore.markSyncPosition(tagQuery, newTagSyncRecords);
      log.info("New tag detection: " + formatTags(newTagSyncRecords));
    }
  }

  @VisibleForTesting
  void refreshOneBatch(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    if (!tagQuery.getContinuousResync() || !allowSync.get()) {
      return;
    }
    final LinkedList<TagSyncRecord> refreshTagSyncRecords = getUnsyncedRecords(tagQuery, tagRefreshSyncStore);
    if (refreshTagSyncRecords.isEmpty()) {
      // Next refresh batch to start again from the beginning
      log.info("Resetting tag refresh to start from the beginning");
      tagRefreshSyncStore.resetSyncPosition(tagQuery);
      return;
    }
    connectApi.upsertWiseTimeTags(refreshTagSyncRecords);
    tagRefreshSyncStore.markSyncPosition(tagQuery, refreshTagSyncRecords);
    log.info("Existing tag refresh: " + formatTags(refreshTagSyncRecords));
  }

  @VisibleForTesting
  void setConnectApi(final ConnectApi connectApi) {
    this.connectApi = connectApi;
  }

  private LinkedList<TagSyncRecord> getUnsyncedRecords(final TagQuery query, final TagSyncStore syncStore) {
    final String syncMarker = syncStore.getSyncMarker(query);

    final List<String> idsToSkip = Stream
        .concat(query.getSkippedIds().stream(), syncStore.getLastSyncedIds(query).stream())
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());

    return database.getTagsToSync(query.getSql(), syncMarker, idsToSkip);
  }

  private boolean hasUpdatedQueries(final List<TagQuery> tagQueries) {
    return !tagQueries.equals(tagQueryProvider.getQueries());
  }
}
