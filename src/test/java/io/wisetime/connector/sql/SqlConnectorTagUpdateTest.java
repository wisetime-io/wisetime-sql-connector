/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.connector.sql.sync.TagSyncRecord;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagQuery;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author shane.xie
 */
class SqlConnectorTagUpdateTest {

  private static ConnectedDatabase mockDatabase = mock(ConnectedDatabase.class);
  private static TagQueryProvider mockTagQueryProvider = mock(TagQueryProvider.class);
  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SyncStore mockDrainSyncStore = mock(SyncStore.class);
  private static SyncStore mockRefreshSyncStore = mock(SyncStore.class);
  private static ConnectApi mockConnectApi = mock(ConnectApi.class);
  private static SqlConnector connector;
  private static SqlConnector mockConnector = mock(SqlConnector.class);
  private static EventBus eventBus = new EventBus();

  @BeforeAll
  static void setUp() {
    connector = new SqlConnector(mockDatabase, mockTagQueryProvider);
    connector.setDrainSyncStore(mockDrainSyncStore);
    connector.setRefreshSyncStore(mockRefreshSyncStore);
    connector.setConnectApi(mockConnectApi);

    eventBus.register(mockConnector);
  }

  @AfterEach
  void tearDown() {
    reset(
        mockDatabase,
        mockTagQueryProvider,
        mockApiClient,
        mockConnectorStore,
        mockDrainSyncStore,
        mockRefreshSyncStore,
        mockConnectApi
    );
  }

  @Test
  void performTagUpdate_no_configured_tag_queries() {
    when(mockTagQueryProvider.getTagQueries()).thenReturn(ImmutableList.of());
    connector.performTagUpdate();
    verifyZeroInteractions(mockDatabase, mockApiClient, mockConnectorStore);
  }

  @Test
  void performTagUpdate_no_tags_to_sync() {
    when(mockTagQueryProvider.getTagQueries())
        .thenReturn(ImmutableList.of(new TagQuery("one", "SELECT 1", "",
            Collections.singletonList("0"), true)));
    when(mockDrainSyncStore.getSyncMarker(any(TagQuery.class))).thenReturn("");
    when(mockDrainSyncStore.getLastSyncedIds(any(TagQuery.class))).thenReturn(ImmutableList.of());
    when(mockDatabase.getTagsToSync(eq("SELECT 1"), eq(""), anyList())).thenReturn(new LinkedList<>());

    connector.performTagUpdate();

    // Verify that no tags were synced
    verify(mockConnectorStore, never()).putString(anyString(), anyString());
    verifyZeroInteractions(mockApiClient);
  }

  @Test
  void performTagUpdate_exception_does_not_prevent_next_run() {
    when(mockTagQueryProvider.getTagQueries())
        .thenReturn(ImmutableList.of(new TagQuery("one", "SELECT 1", "",
            Collections.singletonList("0"), true)));

    when(mockDrainSyncStore.getSyncMarker(any(TagQuery.class)))
        .thenThrow(new RuntimeException("First call throws"))
        .thenReturn("");

    when(mockDrainSyncStore.getLastSyncedIds(any(TagQuery.class))).thenReturn(ImmutableList.of());

    final LinkedList<TagSyncRecord> tagSyncRecords = new LinkedList<>();
    tagSyncRecords.add(randomTagSyncRecord());
    when(mockDatabase.getTagsToSync(eq("SELECT 1"), eq(""), anyList()))
        .thenReturn(tagSyncRecords)
        .thenReturn(new LinkedList<>());

    assertThrows(RuntimeException.class, () -> connector.performTagUpdate());
    connector.performTagUpdate();
    verify(mockDatabase, times(2)).getTagsToSync(anyString(), anyString(), anyList());
  }

  @Test
  void syncAllNewRecords_disallow_sync() {
    connector.syncAllNewRecords(randomTagQuery("cases"), () -> false);
    verifyZeroInteractions(mockDrainSyncStore, mockDatabase, mockApiClient);
  }

  @Test
  void syncAllNewRecords_perform_sync_multiple_database_results() {
    TagQuery query = new TagQuery("cases", "SELECT 1", "1", Collections.singletonList("skipped1"), true);
    when(mockTagQueryProvider.getTagQueries()).thenReturn(ImmutableList.of(query));
    String marker = "10";
    when(mockDrainSyncStore.getSyncMarker(query)).thenReturn(marker);
    when(mockDrainSyncStore.getLastSyncedIds(query)).thenReturn(ImmutableList.of("synced1"));

    final TagSyncRecord queryResults1Record1 = randomTagSyncRecord(fixedTimeMinusMinutes(1));
    final TagSyncRecord queryResults1Record2 = randomTagSyncRecord(fixedTimeMinusMinutes(2));
    final LinkedList<TagSyncRecord> queryResults1 = new LinkedList<>();
    queryResults1.add(queryResults1Record1);
    queryResults1.add(queryResults1Record2);

    final LinkedList<TagSyncRecord> queryResults2 = new LinkedList<>();
    final TagSyncRecord queryResults2Record1 = randomTagSyncRecord(fixedTime());
    queryResults2.add(queryResults2Record1);

    when(mockDatabase.getTagsToSync(query.getSql(), marker, ImmutableList.of("skipped1", "synced1")))
        .thenReturn(queryResults1)
        .thenReturn(queryResults2)
        .thenReturn(new LinkedList<>());

    connector.syncAllNewRecords(query, () -> true);

    // Verify API was called to upsert tags
    ArgumentCaptor<List<TagSyncRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockConnectApi, times(2)).upsertWiseTimeTags(recordsCaptor.capture());
    final List<List<TagSyncRecord>> recordsArguments = recordsCaptor.getAllValues();

    assertThat(recordsArguments.get(0).size())
        .as("Query returns two records")
        .isEqualTo(2);

    assertThat(recordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults1Record1.getTagName());

    assertThat(recordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults1Record2.getTagName());

    assertThat(recordsArguments.get(1).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults2Record1.getTagName());

    // Verify sync store was called to remember sync position
    ArgumentCaptor<TagQuery> queryCaptor = ArgumentCaptor.forClass(TagQuery.class);
    ArgumentCaptor<LinkedList<TagSyncRecord>> syncRecordsCaptor = ArgumentCaptor.forClass(LinkedList.class);
    verify(mockDrainSyncStore, times(2))
        .markSyncPosition(queryCaptor.capture(), syncRecordsCaptor.capture());

    assertThat(queryCaptor.getAllValues().get(0))
        .as("Mark sync position called for the query")
        .isEqualTo(query);

    assertThat(queryCaptor.getAllValues().get(1))
        .as("Mark sync position called for the query")
        .isEqualTo(query);

    final List<LinkedList<TagSyncRecord>> syncRecordsArguments = syncRecordsCaptor.getAllValues();

    assertThat(syncRecordsArguments.get(0).size())
        .as("Query returns two records the first time")
        .isEqualTo(2);

    assertThat(syncRecordsArguments.get(1).size())
        .as("Query returns one record the second time")
        .isEqualTo(1);

    assertThat(syncRecordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults1Record1.getTagName());

    assertThat(syncRecordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults1Record2.getTagName());

    assertThat(syncRecordsArguments.get(1).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(queryResults2Record1.getTagName());
  }

  @Test
  void refreshOneBatch_disallow_sync() {
    connector.refreshOneBatch(randomTagQuery("cases"), () -> false);
    verifyZeroInteractions(mockDrainSyncStore, mockDatabase, mockApiClient);
  }

  @Test
  void refreshOneBatch_reset_sync() {
    TagQuery query = randomTagQuery("cases");
    when(mockTagQueryProvider.getTagQueries()).thenReturn(ImmutableList.of());
    connector.refreshOneBatch(query, () -> true);
    verify(mockRefreshSyncStore, times(1)).resetSyncPosition(query);
    verifyZeroInteractions(mockApiClient);
  }

  @Test
  void refreshOneBatch_perform_sync() {
    TagQuery query = new TagQuery("cases", "SELECT 1", "1", Collections.singletonList("skipped1"), true);
    when(mockTagQueryProvider.getTagQueries()).thenReturn(ImmutableList.of(query));
    String marker = "10";
    when(mockRefreshSyncStore.getSyncMarker(query)).thenReturn(marker);
    when(mockRefreshSyncStore.getLastSyncedIds(query)).thenReturn(ImmutableList.of("synced1"));

    final TagSyncRecord query1Record1 = randomTagSyncRecord(fixedTime());
    final TagSyncRecord query1Record2 = randomTagSyncRecord(fixedTimeMinusMinutes(1));
    final LinkedList<TagSyncRecord> queryResults = new LinkedList<>();
    queryResults.add(query1Record1);
    queryResults.add(query1Record2);

    when(mockDatabase.getTagsToSync(query.getSql(), marker, ImmutableList.of("skipped1", "synced1")))
        .thenReturn(queryResults)
        .thenReturn(new LinkedList<>());

    connector.refreshOneBatch(query, () -> true);

    // Verify API was called to upsert tags
    ArgumentCaptor<List<TagSyncRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockConnectApi, times(1)).upsertWiseTimeTags(recordsCaptor.capture());
    final List<List<TagSyncRecord>> recordsArguments = recordsCaptor.getAllValues();

    assertThat(recordsArguments.get(0).size())
        .as("Query returns two records")
        .isEqualTo(2);

    assertThat(recordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record1.getTagName());

    assertThat(recordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record2.getTagName());

    // Verify sync store was called to remember sync position
    ArgumentCaptor<TagQuery> queryCaptor = ArgumentCaptor.forClass(TagQuery.class);
    ArgumentCaptor<LinkedList<TagSyncRecord>> syncRecordsCaptor = ArgumentCaptor.forClass(LinkedList.class);
    verify(mockRefreshSyncStore, times(1))
        .markSyncPosition(queryCaptor.capture(), syncRecordsCaptor.capture());

    assertThat(queryCaptor.getValue())
        .as("Mark sync position called for the query")
        .isEqualTo(query);

    final List<LinkedList<TagSyncRecord>> syncRecordsArguments = syncRecordsCaptor.getAllValues();

    assertThat(syncRecordsArguments.get(0).size())
        .as("Query returns two records")
        .isEqualTo(2);

    assertThat(syncRecordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record1.getTagName());

    assertThat(syncRecordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record2.getTagName());
  }

  @Test
  void performTagUpdate_send_event() {
    List<TagQuery> tagQueries = Arrays.asList(randomTagQuery("cases"), randomTagQuery("projects"));
    eventBus.post(tagQueries);
    verify(mockConnector).onTagQueriesChanged(tagQueries);
  }
}