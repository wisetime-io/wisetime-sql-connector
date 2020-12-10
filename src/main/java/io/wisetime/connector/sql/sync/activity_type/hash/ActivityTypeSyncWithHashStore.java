/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.hash;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncWithHashStore {

  @VisibleForTesting
  static final String LAST_SYNC_KEY = "ACTIVITY_TYPES_LAST_SYNC";
  @VisibleForTesting
  static final String HASH_KEY = "ACTIVITY_TYPES_HASH";

  private final ConnectorStore connectorStore;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private Function<List<ActivityTypeRecord>, String> hashFunction;

  public ActivityTypeSyncWithHashStore(ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
    hashFunction = activityTypes -> DigestUtils.md5Hex(
        activityTypes.stream()
            .map(activityType -> activityType.getCode() + activityType.getDescription())
            .collect(Collectors.joining()));
  }

  /**
   * Returns true if there was no sync yet or provided activity types differ from the previously synced. Uses hashing for
   * comparison.
   */
  boolean isSynced(List<ActivityTypeRecord> activityTypes) {
    final String activityTypesHash = hashFunction.apply(activityTypes);
    return connectorStore.getString(HASH_KEY)
        .map(hash -> hash.equals(activityTypesHash))
        .orElse(false);
  }

  /**
   * Returns true if there was no sync yet or it was more than a {@link Duration} ago.
   */
  boolean lastSyncedOlderThan(Duration duration) {
    return connectorStore.getLong(LAST_SYNC_KEY)
        .map(lastSync -> System.currentTimeMillis() - lastSync > duration.toMillis())
        .orElse(true);
  }

  void markSynced(List<ActivityTypeRecord> activityTypes) {
    final String activityTypesHash = hashFunction.apply(activityTypes);
    connectorStore.putLong(LAST_SYNC_KEY, System.currentTimeMillis());
    connectorStore.putString(HASH_KEY, activityTypesHash);
  }
}
