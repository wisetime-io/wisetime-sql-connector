/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.hash;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeSyncService;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yehor.lashkul
 */
@Slf4j
public class ActivityTypeSyncWithHashService implements ActivityTypeSyncService {


  private final ConnectApi connectApi;
  private final ConnectedDatabase database;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncWithHashStore activityTypeSyncStore;

  public ActivityTypeSyncWithHashService(
      ConnectorStore connectorStore,
      ConnectApi connectApi,
      ConnectedDatabase database) {
    activityTypeSyncStore = new ActivityTypeSyncWithHashStore(connectorStore);
    this.connectApi = connectApi;
    this.database = database;
  }

  @Override
  public void performActivityTypeUpdate(ActivityTypeQuery query) {
    final List<ActivityTypeRecord> activityTypes = database.getActivityTypes(query);
    final boolean isSynced = activityTypeSyncStore.isSynced(activityTypes);
    final boolean syncedMoreThanDayAgo = activityTypeSyncStore.lastSyncedOlderThan(Duration.ofDays(1));

    // We sync activity types once a day even if they already were synced
    // In such a manner, we defend against hash collisions
    if (!isSynced || syncedMoreThanDayAgo) {
      final String syncSessionId = connectApi.startSyncSession();
      log.info("Sending {} activity types to sync", activityTypes.size());
      connectApi.syncActivityTypes(activityTypes, syncSessionId);
      connectApi.completeSyncSession(syncSessionId);
      activityTypeSyncStore.markSynced(activityTypes);
    }
  }

  @Override
  public void performActivityTypeUpdateSlowLoop(ActivityTypeQuery activityTypeQuery) {
    log.info("There is no slow loop for activity type sync using hashing. Skipping...");
  }
}
