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
import java.util.concurrent.atomic.AtomicReference;
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
  private ActivityTypeSyncWithMarkerStore activityTypeSyncStore;

  public ActivityTypeSyncWithMarkerService(
      ConnectorStore connectorStore,
      ConnectApi connectApi,
      ConnectedDatabase database) {
    activityTypeSyncStore = new ActivityTypeSyncWithMarkerStore(connectorStore);
    this.connectApi = connectApi;
    this.database = database;
  }

  @Override
  public void performActivityTypeUpdate(ActivityTypeQuery query) {
    final AtomicReference<String> syncMarker = new AtomicReference<>(activityTypeSyncStore.getSyncMarker(query));
    final boolean isFirstSync = syncMarker.get().equals(query.getInitialSyncMarker());

    final String syncSessionId = isFirstSync ? getOrStartSyncSession(query) : StringUtils.EMPTY;

    new DrainRun<>(
        () -> database.getActivityTypes(query, syncMarker.get()),
        newBatch -> {
          connectApi.syncActivityTypes(newBatch, syncSessionId);
          syncMarker.set(activityTypeSyncStore.saveSyncMarker(query, newBatch));
          log.info("New activity type detection: " + formatActivityTypes(newBatch));
        }
    ).run();

    if (isFirstSync) {
      connectApi.completeSyncSession(syncSessionId);
      log.info("First run sync is completed withing session {}", syncSessionId);
    }
  }

  private String getOrStartSyncSession(ActivityTypeQuery query) {
    return activityTypeSyncStore.getSyncSession(query)
        .filter(StringUtils::isNotEmpty)
        .orElseGet(() -> {
          final String syncSessionId = connectApi.startSyncSession();
          activityTypeSyncStore.saveSyncSession(query, syncSessionId);
          return syncSessionId;
        });
  }

  @Override
  public void performActivityTypeUpdateSlowLoop(ActivityTypeQuery query) {
    final String syncSessionId = getOrStartRefreshSession(query);
    if (syncOneBatch(query, syncSessionId)) {
      connectApi.completeSyncSession(syncSessionId);
      // Next refresh batch to start again from the beginning
      log.info("Resetting activity types refresh to start from the beginning");
      activityTypeSyncStore.clearRefreshMarker(query);
      activityTypeSyncStore.clearRefreshSession(query);
    }
  }

  private String getOrStartRefreshSession(ActivityTypeQuery query) {
    return activityTypeSyncStore.getRefreshSession(query)
        .filter(StringUtils::isNotEmpty)
        .orElseGet(() -> {
          final String syncSessionId = connectApi.startSyncSession();
          activityTypeSyncStore.saveRefreshSession(query, syncSessionId);
          return syncSessionId;
        });
  }

  // returns true if batch is empty, assuming slow loop is finished
  private boolean syncOneBatch(ActivityTypeQuery query, String syncSessionId) {
    final String refreshSyncMarker = activityTypeSyncStore.getRefreshMarker(query);
    final List<ActivityTypeRecord> refreshActivityTypes = database.getActivityTypes(query, refreshSyncMarker);

    if (refreshActivityTypes.isEmpty()) {
      return true;
    }

    connectApi.syncActivityTypes(refreshActivityTypes, syncSessionId);
    activityTypeSyncStore.saveRefreshMarker(query, refreshActivityTypes);
    log.info("Existing activity types refresh: " + formatActivityTypes(refreshActivityTypes));
    return false;
  }

}
