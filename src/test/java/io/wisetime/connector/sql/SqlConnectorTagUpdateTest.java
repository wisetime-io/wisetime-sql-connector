/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author shane.xie
 */
class SqlConnectorTagUpdateTest {

  private static ConnectedDatabase mockDatabase = mock(ConnectedDatabase.class);
  private static TagQueryProvider mockTagQueryProvider = mock(TagQueryProvider.class);
  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SqlConnector connector;

  @BeforeAll
  static void setUp() {
    connector = new SqlConnector(mockDatabase, mockTagQueryProvider);
  }

  @AfterEach
  void tearDown() {
    reset(mockDatabase, mockTagQueryProvider, mockApiClient, mockConnectorStore);
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
  void performTagUpdate() {
    // TODO(SX)
  }
}