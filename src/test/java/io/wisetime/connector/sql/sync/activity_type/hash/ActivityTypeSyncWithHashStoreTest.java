/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncWithHashStoreTest {

  private final ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private final ActivityTypeSyncWithHashStore activityTypeSyncStore = new ActivityTypeSyncWithHashStore(mockConnectorStore);

  @Test
  void isSynced_notSyncedYet() {
    when(mockConnectorStore.getString(ActivityTypeSyncWithHashStore.HASH_KEY))
        .thenReturn(Optional.empty());

    assertThat(activityTypeSyncStore.isSynced(Collections.emptyList()))
        .as("there was no any sync yet")
        .isFalse();
    assertThat(activityTypeSyncStore.isSynced(randomActivityTypesLis()))
        .as("there was no any sync yet")
        .isFalse();
  }

  @Test
  void isSynced() {
    activityTypeSyncStore.setHashFunction(records -> "same hash");

    when(mockConnectorStore.getString(ActivityTypeSyncWithHashStore.HASH_KEY))
        .thenReturn(Optional.of("same hash"));
    assertThat(activityTypeSyncStore.isSynced(randomActivityTypesLis()))
        .as("same hash -> synced")
        .isTrue();

    when(mockConnectorStore.getString(ActivityTypeSyncWithHashStore.HASH_KEY))
        .thenReturn(Optional.of("another hash"));
    assertThat(activityTypeSyncStore.isSynced(randomActivityTypesLis()))
        .as("another hash -> not synced")
        .isFalse();
  }

  @Test
  void lastSyncedOlderThan_notSyncedYet() {
    when(mockConnectorStore.getLong(ActivityTypeSyncWithHashStore.LAST_SYNC_KEY))
        .thenReturn(Optional.empty());

    assertThat(activityTypeSyncStore.lastSyncedOlderThan(Duration.ofDays(100)))
        .as("there was no any sync yet")
        .isTrue();
  }

  @Test
  void lastSyncedOlderThan() {
    when(mockConnectorStore.getLong(ActivityTypeSyncWithHashStore.LAST_SYNC_KEY))
        .thenReturn(Optional.of(Instant.now()
            .minus(1, ChronoUnit.DAYS)
            .minus(1, ChronoUnit.SECONDS)
            .toEpochMilli()));

    assertThat(activityTypeSyncStore.lastSyncedOlderThan(Duration.ofDays(1)))
        .isTrue();
    assertThat(activityTypeSyncStore.lastSyncedOlderThan(Duration.ofDays(2)))
        .isFalse();
  }

  @Test
  void markSynced() {
    activityTypeSyncStore.setHashFunction(records -> "hash");

    activityTypeSyncStore.markSynced(randomActivityTypesLis());

    // check that hash has been saved
    verify(mockConnectorStore, times(1)).putString(ActivityTypeSyncWithHashStore.HASH_KEY, "hash");
    // check that current time has been saved
    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(mockConnectorStore, times(1)).putLong(eq(ActivityTypeSyncWithHashStore.LAST_SYNC_KEY), captor.capture());
    assertThat(Instant.ofEpochMilli(captor.getValue()))
        .isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
  }

  private List<ActivityTypeRecord> randomActivityTypesLis() {
    return ImmutableList.of(RandomEntities.randomActivityTypeRecord());
  }

}
