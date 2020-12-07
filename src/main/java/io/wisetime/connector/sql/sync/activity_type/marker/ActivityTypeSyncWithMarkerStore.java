/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.marker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * @author yehor.lashkul
 */
@RequiredArgsConstructor
class ActivityTypeSyncWithMarkerStore {

  private static final String CODES_DELIMITER = "@@@";

  private final ConnectorStore connectorStore;
  private final String keySpace;

  public ActivityTypeSyncWithMarkerStore(ConnectorStore connectorStore) {
    this(connectorStore, "");
  }

  void markSyncPosition(ActivityTypeQuery query, List<ActivityTypeRecord> activityTypes) {
    Preconditions.checkArgument(activityTypes.size() > 0, "activity types can't be empty");

    final String latestMarker = activityTypes.get(activityTypes.size() - 1).getSyncMarker();
    connectorStore.putString(syncMarkerKey(query), latestMarker);

    final String latestSyncedCodes = activityTypes.stream()
        .filter(activityType -> activityType.getSyncMarker().equals(latestMarker))
        .map(ActivityTypeRecord::getCode)
        .collect(Collectors.joining(CODES_DELIMITER));
    connectorStore.putString(lastSyncedCodesKey(query), latestSyncedCodes);
  }

  String getSyncMarker(ActivityTypeQuery query) {
    return connectorStore.getString(syncMarkerKey(query))
        .orElse(query.getInitialSyncMarker());
  }

  void clearSyncMarker(ActivityTypeQuery query) {
    connectorStore.putString(syncMarkerKey(query), null);
  }

  List<String> getLastSyncedCodes(ActivityTypeQuery query) {
    return connectorStore.getString(lastSyncedCodesKey(query))
        .map(refs -> refs.split(CODES_DELIMITER))
        .map(Arrays::asList)
        .orElse(List.of());
  }

  void saveSyncSession(ActivityTypeQuery query, String syncSessionId) {
    connectorStore.putString(syncSessionKey(query), syncSessionId);
  }

  Optional<String> getSyncSession(ActivityTypeQuery query) {
    return connectorStore.getString(syncSessionKey(query));
  }

  void clearSyncSession(ActivityTypeQuery query) {
    connectorStore.putString(syncSessionKey(query), null);
  }

  @VisibleForTesting
  String syncMarkerKey(final ActivityTypeQuery query) {
    return keySpace + query.hashCode() + "_activity_type_sync_marker";
  }

  @VisibleForTesting
  String lastSyncedCodesKey(final ActivityTypeQuery query) {
    return keySpace + query.hashCode() + "_activity_type_last_sync_codes";
  }

  @VisibleForTesting
  String syncSessionKey(final ActivityTypeQuery query) {
    return keySpace + query.hashCode() + "_activity_type_sync_session";
  }
}
