/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.RandomEntities.fixedTime;
import static io.wisetime.connector.sql.RandomEntities.fixedTimeMinusMinutes;
import static io.wisetime.connector.sql.RandomEntities.randomTagQuery;
import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import com.google.common.eventbus.EventBus;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import java.util.Arrays;
import java.util.Collections;
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
  private static SqlConnector mockConnector = mock(SqlConnector.class);
  private static EventBus eventBus = new EventBus();

  @BeforeAll
  static void setUp() {
    connector = new SqlConnector(mockDatabase, mockTagQueryProvider);
    connector.setSyncStore(mockSyncStore);
    connector.setConnectApi(mockConnectApi);

    eventBus.register(mockConnector);
  }

  @AfterEach
  void tearDown() {
    reset(mockDatabase, mockTagQueryProvider, mockApiClient, mockConnectorStore, mockSyncStore, mockConnectApi);
  }

  @Test
  void performTagUpdate_fails_non_unique_query_names() {
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(
        new TagQuery("name", "", "", Collections.singletonList("0")),
        new TagQuery("name", "", "", Collections.singletonList("0"))
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
        .thenReturn(ImmutableList.of(new TagQuery("one", "SELECT 1", "",
            Collections.singletonList("0"))));
    when(mockSyncStore.getSyncMarker(randomTagQuery("one", ""))).thenReturn("");
    when(mockSyncStore.getLastSyncedIds(randomTagQuery("one", ""))).thenReturn(ImmutableList.of());
    when(mockDatabase.getTagsToSync(eq("SELECT 1"), eq(""), anyList())).thenReturn(new LinkedList<>());

    connector.performTagUpdate();

    // Verify that no tags were synced
    verify(mockConnectorStore, never()).putString(anyString(), anyString());
    verifyZeroInteractions(mockApiClient);
  }

  @Test
  void performTagUpdate_sync_tags_multiple_queries() {
    TagQuery first = new TagQuery("one", "SELECT 1", "1", Collections.singletonList("skipped1"));
    TagQuery second = new TagQuery("two", "SELECT 2", "2", Collections.singletonList(""));
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(first, second));
    String markerForFirst = "10";
    when(mockSyncStore.getSyncMarker(first)).thenReturn(markerForFirst);
    String markerForSecond = "20";
    when(mockSyncStore.getSyncMarker(second)).thenReturn(markerForSecond);
    when(mockSyncStore.getLastSyncedIds(first)).thenReturn(ImmutableList.of("synced1"));
    when(mockSyncStore.getLastSyncedIds(second)).thenReturn(ImmutableList.of("synced2"));

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

    when(mockDatabase.getTagsToSync(first.getSql(), markerForFirst, ImmutableList.of("skipped1", "synced1")))
        .thenReturn(query1Results)
        .thenReturn(new LinkedList<>());

    when(mockDatabase.getTagsToSync(second.getSql(), markerForSecond, ImmutableList.of("synced2")))
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
    ArgumentCaptor<TagQuery> namespaceCaptor = ArgumentCaptor.forClass(TagQuery.class);
    ArgumentCaptor<LinkedList<TagSyncRecord>> syncRecordsCaptor = ArgumentCaptor.forClass(LinkedList.class);
    verify(mockSyncStore, times(2))
        .markSyncPosition(namespaceCaptor.capture(), syncRecordsCaptor.capture());

    final List<TagQuery> namespaceArguments = namespaceCaptor.getAllValues();
    assertThat(namespaceArguments.get(0).getName())
        .as("One position sync per namespace")
        .isEqualTo("one");
    assertThat(namespaceArguments.get(1).getName())
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

  @Test
  void performTagUpdate_sync_tags_multiple_runs() {
    TagQuery query = new TagQuery("cases", "SELECT 1", "1", Collections.singletonList("skip"));
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(query));
    when(mockSyncStore.getSyncMarker(query))
        .thenReturn("10")
        .thenReturn("20");
    when(mockSyncStore.getLastSyncedIds(query))
        .thenReturn(ImmutableList.of())
        .thenReturn(ImmutableList.of("synced"));

    final LinkedList<TagSyncRecord> firstResults = new LinkedList<>();
    firstResults.add(randomTagSyncRecord());
    final LinkedList<TagSyncRecord> secondResults = new LinkedList<>();
    secondResults.add(randomTagSyncRecord());

    when(mockDatabase.getTagsToSync(query.getSql(), "10", ImmutableList.of("skip")))
        .thenReturn(firstResults)
        .thenReturn(new LinkedList<>());
    when(mockDatabase.getTagsToSync(query.getSql(), "20", ImmutableList.of("skip", "synced")))
        .thenReturn(secondResults)
        .thenReturn(new LinkedList<>());

    connector.performTagUpdate();
    verify(mockConnectApi, times(2)).upsertWiseTimeTags(anyCollection());
    verify(mockSyncStore, times(2)).markSyncPosition(any(), any());
  }

  @Test
  void performTagUpdate_send_event() {
    List<TagQuery> tagQueries = Arrays.asList(
        new TagQuery("cases", "SELECT 1", "1", Collections.singletonList("skip")),
        new TagQuery("cases", "SELECT 1", "1", Collections.singletonList("skip")));
    eventBus.post(tagQueries);

    verify(mockConnector).receiveTagQueriesChange(any());
  }
}