/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.Setter;

/**
 * @author yehor.lashkul
 */
public class ActivityTypeSyncStore {

  @VisibleForTesting
  static final String LAST_SYNC_KEY = "ACTIVITY_TYPES_LAST_SYNC";
  @VisibleForTesting
  static final String HASH_KEY = "ACTIVITY_TYPES_HASH";

  private final ConnectorStore connectorStore;
  @Setter
  @VisibleForTesting
  private Function<List<ActivityTypeRecord>, String> hashFunction;

  public ActivityTypeSyncStore(ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
    hashFunction = activityTypes -> String.format("%d_%d", activityTypes.size(), Objects.hash(activityTypes.toArray()));
  }

  /**
   * Returns true if there was no sync yet or provided activity types differ from the previously synced. Uses hashing for
   * comparison.
   */
  public boolean isSynced(List<ActivityTypeRecord> activityTypes) {
    final String activityTypesHash = hashFunction.apply(activityTypes);
    return connectorStore.getString(HASH_KEY)
        .map(hash -> hash.equals(activityTypesHash))
        .orElse(false);
  }

  /**
   * Returns true if there was no sync yet or it was more than a {@link Duration} ago.
   */
  public boolean lastSyncedOlderThan(Duration duration) {
    return connectorStore.getLong(LAST_SYNC_KEY)
        .map(lastSync -> System.currentTimeMillis() - lastSync > duration.toMillis())
        .orElse(true);
  }

  public void markSynced(List<ActivityTypeRecord> activityTypes) {
    final String activityTypesHash = hashFunction.apply(activityTypes);
    connectorStore.putLong(LAST_SYNC_KEY, System.currentTimeMillis());
    connectorStore.putString(HASH_KEY, activityTypesHash);
  }
}
