/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.collect.ImmutableList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Optional;

import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.TagQuery;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagQuery;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * @author shane.xie
 */
class SyncStoreTest {

  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);

  @BeforeEach
  void setUp() {
    reset(mockConnectorStore);
  }

  @Test
  void tag_queries_are_independent() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(tagSyncRecord);

    TagQuery cases = RandomEntities.randomTagQuery("cases");
    syncStore.markSyncPosition(cases, tagSyncRecords);
    verify(mockConnectorStore).putString(cases.hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString(cases.hashCode() + "_last_synced_ids", tagSyncRecord.getId());

    TagQuery projects = RandomEntities.randomTagQuery("projects");
    syncStore.markSyncPosition(projects, tagSyncRecords);
    verify(mockConnectorStore).putString(projects.hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore).putString(projects.hashCode() + "_last_synced_ids", tagSyncRecord.getId());
  }

  @Test
  void default_key_space_is_empty_string() {
    final String defaultKeySpace = "";
    final SyncStore storeWithDefaultKeySpace = new SyncStore(mockConnectorStore);

    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(tagSyncRecord);

    TagQuery cases = RandomEntities.randomTagQuery("cases");
    storeWithDefaultKeySpace.markSyncPosition(cases, tagSyncRecords);

    // The default key space should not be changed without due consideration for backwards compatibility
    verify(mockConnectorStore)
        .putString(defaultKeySpace + cases.hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());
    verify(mockConnectorStore)
        .putString(defaultKeySpace + cases.hashCode() + "_last_synced_ids", tagSyncRecord.getId());
  }

  @Test
  void key_spaces_are_independent() {
    TagQuery query = RandomEntities.randomTagQuery("cases");
    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(tagSyncRecord);

    // Store 1, same query
    final SyncStore syncStore1 = new SyncStore(mockConnectorStore, "1");

    syncStore1.markSyncPosition(query, tagSyncRecords);
    verify(mockConnectorStore)
        .putString("1" + query.hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());

    reset(mockConnectorStore);
    syncStore1.getSyncMarker(query);
    verify(mockConnectorStore).getString("1" + query.hashCode() + "_sync_marker");

    reset(mockConnectorStore);
    syncStore1.getLastSyncedIds(query);
    verify(mockConnectorStore).getString("1" + query.hashCode() + "_last_synced_ids");

    // Store 2, same query
    final SyncStore syncStore2 = new SyncStore(mockConnectorStore, "2");

    syncStore2.markSyncPosition(query, tagSyncRecords);
    verify(mockConnectorStore)
        .putString("2" + query.hashCode() + "_sync_marker", tagSyncRecord.getSyncMarker());

    reset(mockConnectorStore);
    syncStore2.getSyncMarker(query);
    verify(mockConnectorStore).getString("2" + query.hashCode() + "_sync_marker");

    reset(mockConnectorStore);
    syncStore2.getLastSyncedIds(query);
    verify(mockConnectorStore).getString("2" + query.hashCode() + "_last_synced_ids");
  }

  @Test
  void markSyncPosition_nothing_to_persist() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
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

    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    syncStore.markSyncPosition(tagQuery, tagSyncRecords);

    // Verify that the persisted sync marker is the last ID
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_sync_marker", "2");

    // Verify that the persisted ID is the one with the largest marker value
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_last_synced_ids", tagSyncRecords.get(3).getId());
  }

  @Test
  void markSyncPosition_datetime_marker() {
    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(randomTagSyncRecord(fixedTimeMinusMinutes(10)));
    tagSyncRecords.add(randomTagSyncRecord(fixedTime()));
    tagSyncRecords.add(randomTagSyncRecord(fixedTime()));
    TagQuery tagQuery = RandomEntities.randomTagQuery("cases");

    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    syncStore.markSyncPosition(tagQuery, tagSyncRecords);

    // Verify that the persisted sync marker is the most recent time
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_sync_marker", fixedTime());

    // Verify that the persisted ids are the two most recent with the same sync marker
    final String persistedIds = tagSyncRecords.get(2).getId() + "@@@" + tagSyncRecords.get(1).getId();
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_last_synced_ids", persistedIds);
  }

  @Test
  void markSyncPosition_current_batch_has_same_marker_as_previous() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    TagQuery tagQuery = RandomEntities.randomTagQuery("cases");

    final LinkedList<TagSyncRecord> tagSyncRecordsBatch1 = new LinkedList<>();
    tagSyncRecordsBatch1.add(randomTagSyncRecord(fixedTimeMinusMinutes(10)));
    tagSyncRecordsBatch1.add(randomTagSyncRecord(fixedTime()));
    tagSyncRecordsBatch1.add(randomTagSyncRecord(fixedTime()));
    syncStore.markSyncPosition(tagQuery, tagSyncRecordsBatch1);

    when(mockConnectorStore.getString(tagQuery.hashCode() + "_sync_marker"))
        .thenReturn(Optional.of(fixedTime()));

    when(mockConnectorStore.getString(tagQuery.hashCode() + "_last_synced_ids"))
        .thenReturn(Optional.of(tagSyncRecordsBatch1.get(2).getId() + "@@@" + tagSyncRecordsBatch1.get(1).getId()));

    final LinkedList<TagSyncRecord> tagSyncRecordsBatch2 = new LinkedList<>();
    tagSyncRecordsBatch2.add(randomTagSyncRecord(fixedTime()));
    tagSyncRecordsBatch2.add(randomTagSyncRecord(fixedTime()));
    syncStore.markSyncPosition(tagQuery, tagSyncRecordsBatch2);

    final String persistedIds = tagSyncRecordsBatch1.get(2).getId()
        + "@@@" + tagSyncRecordsBatch1.get(1).getId()
        + "@@@" + tagSyncRecordsBatch2.get(1).getId()
        + "@@@" + tagSyncRecordsBatch2.get(0).getId();

    // Verify that the current synced IDs are appended to previous list
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_last_synced_ids", persistedIds);
  }

  @Test
  void resetSyncPosition() {
    final TagQuery tagQuery = randomTagQuery("cases");
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    syncStore.resetSyncPosition(tagQuery);

    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_sync_marker", tagQuery.getInitialSyncMarker());
    verify(mockConnectorStore, times(1))
        .putString(tagQuery.hashCode() + "_last_synced_ids", "");
  }

  @Test
  void getSyncMarker_default() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    when(mockConnectorStore.getString("cases_sync_marker"))
        .thenReturn(Optional.empty());
    TagQuery cases = RandomEntities.randomTagQuery("cases");
    assertThat(syncStore.getSyncMarker(cases))
        .as("Pass on sync marker from the store")
        .isEqualTo(cases.getInitialSyncMarker());
  }

  @Test
  void getSyncMarker_from_store() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    final String syncMarker = fixedTime();
    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.of(syncMarker));
    assertThat(syncStore.getSyncMarker(RandomEntities.randomTagQuery("cases")))
        .as("Pass on sync marker from the store")
        .isEqualTo(syncMarker);
  }

  @Test
  void getLastSyncedIds() {
    final SyncStore syncStore = new SyncStore(mockConnectorStore);
    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.of("1@@@2"));
    assertThat(syncStore.getLastSyncedIds(RandomEntities.randomTagQuery("cases")))
        .as("Pass on sync marker from the store")
        .isEqualTo(ImmutableList.of("1", "2"));
  }
}