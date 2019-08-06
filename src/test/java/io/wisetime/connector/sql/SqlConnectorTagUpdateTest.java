/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * @author shane.xie
 */
class SqlConnectorTagUpdateTest {

  private static ConnectedDatabase mockDatabase = mock(ConnectedDatabase.class);
  private static TagQueryProvider mockTagQueryProvider = mock(TagQueryProvider.class);
  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SyncStore mockSyncStore = mock(SyncStore.class);
  private static ConnectApi mockConnectApi = mock(ConnectApi.class);
  private static SqlConnector connector;

  @BeforeAll
  static void setUp() {
    connector = new SqlConnector(mockDatabase, mockTagQueryProvider);
    connector.setSyncStore(mockSyncStore);
    connector.setConnectApi(mockConnectApi);
  }

  @AfterEach
  void tearDown() {
    reset(mockDatabase, mockTagQueryProvider, mockApiClient, mockConnectorStore, mockSyncStore);
  }

  @Test
  void performTagUpdate_fails_non_unique_query_names() {
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(
        new TagQuery("name", "", ""),
        new TagQuery("name", "", "")
    ));
    assertThrows(IllegalArgumentException.class, () -> connector.performTagUpdate());
  }

  @Test
  void performTagUpdate_no_configured_tag_queries() {
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of());
    connector.performTagUpdate();
    verifyZeroInteractions(mockDatabase, mockApiClient, mockConnectorStore);
  }

  @Test
  void performTagUpdate_no_tags_to_sync() {
    when(mockTagQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(new TagQuery("one", "SELECT 1", "")));
    when(mockSyncStore.getSyncMarker("one", "")).thenReturn("");
    when(mockSyncStore.getLastSyncedReferences("one")).thenReturn(ImmutableList.of());
    when(mockDatabase.getTagsToSync(eq("SELECT 1"), eq(""), anyList())).thenReturn(new LinkedList<>());

    connector.performTagUpdate();

    // Verify that no tags were synced
    verify(mockConnectorStore, never()).putString(anyString(), anyString());
    verifyZeroInteractions(mockApiClient);
  }

  @Test
  void performTagUpdate_sync_tags() {
    when(mockTagQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(
            new TagQuery("one", "SELECT 1", "1"),
            new TagQuery("two", "SELECT 2", "2")
        ));
    when(mockSyncStore.getSyncMarker(eq("one"), eq("1"))).thenReturn("10");
    when(mockSyncStore.getSyncMarker(eq("two"), eq("2"))).thenReturn("20");
    when(mockSyncStore.getLastSyncedReferences(eq("one"))).thenReturn(ImmutableList.of("ref1"));
    when(mockSyncStore.getLastSyncedReferences(eq("two"))).thenReturn(ImmutableList.of("ref2"));

    // Two tags for query one
    final TagSyncRecord query1Record1 = randomTagSyncRecord(fixedTime());
    final TagSyncRecord query1Record2 = randomTagSyncRecord(fixedTimeMinusMinutes(1));
    final LinkedList<TagSyncRecord> query1Results = new LinkedList<>();
    query1Results.add(query1Record1);
    query1Results.add(query1Record2);
    // One tag for query two
    final TagSyncRecord query2Record = randomTagSyncRecord();
    final LinkedList<TagSyncRecord> query2Results = new LinkedList<>();
    query2Results.add(query2Record);

    when(mockDatabase.getTagsToSync("SELECT 1", "10", ImmutableList.of("ref1")))
        .thenReturn(query1Results)
        .thenReturn(new LinkedList<>());

    when(mockDatabase.getTagsToSync("SELECT 2", "20", ImmutableList.of("ref2")))
        .thenReturn(query2Results)
        .thenReturn(new LinkedList<>());

    connector.performTagUpdate();

    // Verify API was called to upsert tags
    ArgumentCaptor<List<TagSyncRecord>> recordsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockConnectApi, times(2)).upsertWiseTimeTags(recordsCaptor.capture());
    final List<List<TagSyncRecord>> recordsArguments = recordsCaptor.getAllValues();

    assertThat(recordsArguments.get(0).size())
        .as("First query returns two records")
        .isEqualTo(2);

    assertThat(recordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record1.getTagName());

    assertThat(recordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record2.getTagName());

    assertThat(recordsArguments.get(1).size())
        .as("Second query returns one record")
        .isEqualTo(1);

    assertThat(recordsArguments.get(1).get(0).getTagName())
        .as("Record matches query result")
        .isEqualTo(query2Record.getTagName());

    // Verify sync store was called to remember sync position
    ArgumentCaptor<String> namespaceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LinkedList<TagSyncRecord>> syncRecordsCaptor = ArgumentCaptor.forClass(LinkedList.class);
    verify(mockSyncStore, times(2))
        .markSyncPosition(namespaceCaptor.capture(), syncRecordsCaptor.capture());

    final List<String> namespaceArguments = namespaceCaptor.getAllValues();
    assertThat(namespaceArguments.get(0))
        .as("One position sync per namespace")
        .isEqualTo("one");
    assertThat(namespaceArguments.get(1))
        .as("One position sync per namespace")
        .isEqualTo("two");

    final List<LinkedList<TagSyncRecord>> syncRecordsArguments = syncRecordsCaptor.getAllValues();

    assertThat(syncRecordsArguments.get(0).size())
        .as("First query returns two records")
        .isEqualTo(2);

    assertThat(syncRecordsArguments.get(0).get(0).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record1.getTagName());

    assertThat(syncRecordsArguments.get(0).get(1).getTagName())
        .as("Record matches per returned order")
        .isEqualTo(query1Record2.getTagName());

    assertThat(syncRecordsArguments.get(1).size())
        .as("Second query returns one record")
        .isEqualTo(1);

    assertThat(syncRecordsArguments.get(1).get(0).getTagName())
        .as("Record matches query result")
        .isEqualTo(query2Record.getTagName());
  }
}