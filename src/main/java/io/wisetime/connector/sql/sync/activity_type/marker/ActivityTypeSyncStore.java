/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.marker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.util.List;
import java.util.Optional;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncStore {

  private final ConnectorStore connectorStore;

  public ActivityTypeSyncStore(ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
  }

  String saveSyncMarker(ActivityTypeQuery query, List<ActivityTypeRecord> activityTypes) {
    Preconditions.checkArgument(activityTypes.size() > 0, "activity types can't be empty");
    final String latestMarker = activityTypes.get(activityTypes.size() - 1).getSyncMarker();
    connectorStore.putString(syncMarkerKey(query), latestMarker);
    return latestMarker;
  }

  String getSyncMarker(ActivityTypeQuery query) {
    return connectorStore.getString(syncMarkerKey(query))
        .orElse(query.getInitialSyncMarker());
  }

  String saveRefreshMarker(ActivityTypeQuery query, List<ActivityTypeRecord> activityTypes) {
    Preconditions.checkArgument(activityTypes.size() > 0, "activity types can't be empty");
    final String latestMarker = activityTypes.get(activityTypes.size() - 1).getSyncMarker();
    connectorStore.putString(refreshMarkerKey(query), latestMarker);
    return latestMarker;
  }

  String getRefreshMarker(ActivityTypeQuery query) {
    return connectorStore.getString(refreshMarkerKey(query))
        .orElse(query.getInitialSyncMarker());
  }

  void clearRefreshMarker(ActivityTypeQuery query) {
    connectorStore.putString(refreshMarkerKey(query), null);
  }

  void saveSyncSession(ActivityTypeQuery query, String syncSessionId) {
    connectorStore.putString(syncSessionKey(query), syncSessionId);
  }

  Optional<String> getSyncSession(ActivityTypeQuery query) {
    return connectorStore.getString(syncSessionKey(query));
  }

  void saveRefreshSession(ActivityTypeQuery query, String syncSessionId) {
    connectorStore.putString(refreshSessionKey(query), syncSessionId);
  }

  Optional<String> getRefreshSession(ActivityTypeQuery query) {
    return connectorStore.getString(refreshSessionKey(query));
  }

  void clearRefreshSession(ActivityTypeQuery query) {
    connectorStore.putString(refreshSessionKey(query), null);
  }

  @VisibleForTesting
  String syncMarkerKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_marker";
  }

  @VisibleForTesting
  String refreshMarkerKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_refresh_marker";
  }

  @VisibleForTesting
  String syncSessionKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_session";
  }

  @VisibleForTesting
  String refreshSessionKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_refresh_session";
  }
}
