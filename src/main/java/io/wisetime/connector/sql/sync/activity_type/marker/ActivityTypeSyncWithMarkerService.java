/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.marker;

import static io.wisetime.connector.sql.format.LogFormatter.formatActivityTypes;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.queries.DrainRun;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeSyncService;
import java.util.List;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yehor.lashkul
 */
@Slf4j
public class ActivityTypeSyncWithMarkerService implements ActivityTypeSyncService {

  private final ConnectApi connectApi;
  private final ConnectedDatabase database;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncWithMarkerStore activityTypeDrainSyncStore;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncWithMarkerStore activityTypeRefreshSyncStore;

  public ActivityTypeSyncWithMarkerService(
      ConnectorStore connectorStore,
      ConnectApi connectApi,
      ConnectedDatabase database) {
    activityTypeDrainSyncStore = new ActivityTypeSyncWithMarkerStore(connectorStore);
    activityTypeRefreshSyncStore = new ActivityTypeSyncWithMarkerStore(connectorStore, "refresh_");
    this.connectApi = connectApi;
    this.database = database;
  }

  @Override
  public void performActivityTypeUpdate(ActivityTypeQuery query) {
    final String syncMarker = activityTypeDrainSyncStore.getSyncMarker(query);
    final boolean isFirstSync = syncMarker.equals(query.getInitialSyncMarker());

    final String syncSessionId = isFirstSync ? getOrStartSyncSession(activityTypeDrainSyncStore, query) : StringUtils.EMPTY;
    new DrainRun<>(
        () -> getUnsyncedRecords(query, activityTypeDrainSyncStore),
        newBatch -> {
          connectApi.syncActivityTypes(newBatch, syncSessionId);
          activityTypeDrainSyncStore.markSyncPosition(query, newBatch);
          log.info("New activity type detection: " + formatActivityTypes(newBatch));
        }
    ).run();

    if (isFirstSync) {
      connectApi.completeSyncSession(syncSessionId);
      log.info("First run sync is completed withing session {}", syncSessionId);
    }
  }

  @Override
  public void performActivityTypeUpdateSlowLoop(ActivityTypeQuery query) {
    final String syncSessionId = getOrStartSyncSession(activityTypeRefreshSyncStore, query);
    if (refreshOneBatch(query, syncSessionId)) {
      connectApi.completeSyncSession(syncSessionId);
      // Next refresh batch to start again from the beginning
      log.info("Resetting activity types refresh to start from the beginning");
      activityTypeRefreshSyncStore.clearSyncMarker(query);
      activityTypeRefreshSyncStore.clearSyncSession(query);
    }
  }

  private String getOrStartSyncSession(ActivityTypeSyncWithMarkerStore store, ActivityTypeQuery query) {
    return store.getSyncSession(query)
        .filter(StringUtils::isNotEmpty)
        .orElseGet(() -> {
          final String syncSessionId = connectApi.startSyncSession();
          store.saveSyncSession(query, syncSessionId);
          return syncSessionId;
        });
  }

  // returns true if batch is empty, assuming slow loop is finished
  private boolean refreshOneBatch(ActivityTypeQuery query, String syncSessionId) {
    final List<ActivityTypeRecord> refreshActivityTypes = getUnsyncedRecords(query, activityTypeRefreshSyncStore);

    if (refreshActivityTypes.isEmpty()) {
      return true;
    }

    connectApi.syncActivityTypes(refreshActivityTypes, syncSessionId);
    activityTypeRefreshSyncStore.markSyncPosition(query, refreshActivityTypes);
    log.info("Existing activity types refresh: " + formatActivityTypes(refreshActivityTypes));
    return false;
  }

  private List<ActivityTypeRecord> getUnsyncedRecords(final ActivityTypeQuery query,
      final ActivityTypeSyncWithMarkerStore syncStore) {
    final String syncMarker = syncStore.getSyncMarker(query);
    final List<String> lastSyncedCodesToSkip = syncStore.getLastSyncedCodes(query);
    return database.getActivityTypes(query, syncMarker, lastSyncedCodesToSkip);
  }

}
