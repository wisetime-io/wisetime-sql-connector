/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.TagQuery;
import java.util.LinkedList;
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
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(tagSyncRecord);

    TagQuery cases = RandomEntities.randomTagQuery("cases");
    syncStore.markSyncPosition(cases, tagSyncRecords);
    verify(mockConnectorStore).putString(
        cases.getName() + "_" + cases.getSql().hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString(cases.getName() + "_" + cases.getSql().hashCode() + "_last_synced_ids",
        tagSyncRecord.getId());

    TagQuery projects = RandomEntities.randomTagQuery("projects");
    syncStore.markSyncPosition(projects, tagSyncRecords);
    verify(mockConnectorStore).putString(projects.getName() + "_" + projects.getSql().hashCode() + "_sync_marker",
        tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString(projects.getName() + "_" + projects.getSql().hashCode() + "_last_synced_ids",
        tagSyncRecord.getId());
  }

  @Test
  void markSyncPosition_nothing_to_persist() {
    syncStore.markSyncPosition(RandomEntities.randomTagQuery("cases"), new LinkedList<>());
    verify(mockConnectorStore, never()).putString(anyString(), anyString());
  }

  @Test
  void markSyncPosition_numeric_marker() {
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(randomTagSyncRecord("-1"));
    tagSyncRecords.add(randomTagSyncRecord("0"));
    tagSyncRecords.add(randomTagSyncRecord("1"));
    tagSyncRecords.add(randomTagSyncRecord("2"));
    TagQuery tagQuery = RandomEntities.randomTagQuery("cases");
    syncStore.markSyncPosition(tagQuery, tagSyncRecords);

    // Verify that the persisted sync marker is the last ID
    verify(mockConnectorStore, times(1)).putString(
        tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_sync_marker", "2");

    // Verify that the persisted ID is the one with the largest marker value
    verify(mockConnectorStore, times(1))
        .putString(
            tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_last_synced_ids",
            tagSyncRecords.get(3).getId()
      );
  }

  @Test
  void markSyncPosition_datetime_marker() {
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(randomTagSyncRecord(fixedTimeMinusMinutes(10)));
    tagSyncRecords.add(randomTagSyncRecord(fixedTime()));
    tagSyncRecords.add(randomTagSyncRecord(fixedTime()));
    TagQuery tagQuery = RandomEntities.randomTagQuery("cases");
    syncStore.markSyncPosition(tagQuery, tagSyncRecords);

    // Verify that the persisted sync marker is the most recent time
    verify(mockConnectorStore, times(1)).putString(
        tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_sync_marker", fixedTime());

    // Verify that the persisted ids are the two most recent with the same sync marker
    final String persistedIds = tagSyncRecords.get(2).getId() + "@@@" + tagSyncRecords.get(1).getId();
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_last_synced_ids", persistedIds);
  }

  @Test
  void markSyncPosition_current_batch_has_same_marker_as_previous() {
    // TODO(DG): Verify that the current synced IDs are appended to previous list
    fail("Please implement this test");
  }

  @Test
  void getSyncMarker_default() {
    when(mockConnectorStore.getString("cases_sync_marker"))
        .thenReturn(Optional.empty());
    TagQuery cases = RandomEntities.randomTagQuery("cases");
    assertThat(syncStore.getSyncMarker(cases))
        .as("Pass on sync marker from the store")
        .isEqualTo(cases.getInitialSyncMarker());
  }

  @Test
  void getSyncMarker_from_store() {
    final String syncMarker = fixedTime();
    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.of(syncMarker));
    assertThat(syncStore.getSyncMarker(RandomEntities.randomTagQuery("cases")))
        .as("Pass on sync marker from the store")
        .isEqualTo(syncMarker);
  }

  @Test
  void getLastSyncedIds() {
    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.of("1@@@2"));
    assertThat(syncStore.getLastSyncedIds(RandomEntities.randomTagQuery("cases")))
        .as("Pass on sync marker from the store")
        .isEqualTo(ImmutableList.of("1", "2"));
  }
}