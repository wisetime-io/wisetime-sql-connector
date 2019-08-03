/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author shane.xie
 */
class SyncStoreTest {

  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SyncStore syncStore = new SyncStore(mockConnectorStore);

  @BeforeEach
  void setUp() {
    reset(mockConnectorStore);
  }

  @Test
  void namespaces_are_independent() {
    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();

    syncStore.markSyncPosition("cases", ImmutableList.of(tagSyncRecord));
    verify(mockConnectorStore).putString("cases_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString("cases_last_synced_references", tagSyncRecord.getReference());

    syncStore.markSyncPosition("projects", ImmutableList.of(tagSyncRecord));
    verify(mockConnectorStore).putString("projects_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString("projects_last_synced_references", tagSyncRecord.getReference());
  }

  @Test
  void markSyncPosition_nothing_to_persist() {
    syncStore.markSyncPosition("cases", ImmutableList.of());
    verify(mockConnectorStore, never()).putString(anyString(), anyString());
  }

  @Test
  void markSyncPosition_fail_sync_tags_not_sorted_with_most_recent_first() {
    final List<TagSyncRecord> tagSyncRecords = ImmutableList.of(
        randomTagSyncRecord(fixedTimeMinusMinutes(2)),
        randomTagSyncRecord(fixedTimeMinusMinutes(1))
    );
    assertThrows(IllegalArgumentException.class, () ->
        syncStore.markSyncPosition("cases", tagSyncRecords)
    );
  }

  @Test
  void markSyncPosition() {
    final List<TagSyncRecord> tagSyncRecords = ImmutableList.of(
        randomTagSyncRecord(fixedTime()),
        randomTagSyncRecord(fixedTime()),
        randomTagSyncRecord(fixedTimeMinusMinutes(10))
    );
    syncStore.markSyncPosition("cases", tagSyncRecords);

    // Verify that the persisted sync marker is the most recent time
    verify(mockConnectorStore, times(1)).putString("cases_sync_marker", fixedTime());

    // Verify that the persisted references are the two most recent with the same sync marker
    final String persistedReferences = tagSyncRecords.get(0).getReference() + "," + tagSyncRecords.get(1).getReference();
    verify(mockConnectorStore, times(1))
        .putString("cases_last_synced_references", persistedReferences);
  }

  @Test
  void getSyncMarker_default() {
    when(mockConnectorStore.getString("cases_sync_marker"))
        .thenReturn(Optional.empty());
    assertThat(syncStore.getSyncMarker("cases", "default"))
        .as("Pass on sync marker from the store")
        .isEqualTo("default");
  }

  @Test
  void getSyncMarker_from_store() {
    final String syncMarker = fixedTime();
    when(mockConnectorStore.getString("cases_sync_marker"))
        .thenReturn(Optional.of(syncMarker));
    assertThat(syncStore.getSyncMarker("cases", ""))
        .as("Pass on sync marker from the store")
        .isEqualTo(syncMarker);
  }

  @Test
  void getLastSyncedReferences() {
    when(mockConnectorStore.getString("cases_last_synced_references"))
        .thenReturn(Optional.of("1,2"));
    assertThat(syncStore.getLastSyncedReferences("cases"))
        .as("Pass on sync marker from the store")
        .isEqualTo(ImmutableList.of("1", "2"));
  }
}