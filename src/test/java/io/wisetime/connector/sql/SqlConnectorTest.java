/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.generated.connect.TimeGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import spark.Request;

/**
 * @author shane.xie
 */
class SqlConnectorTest {

  private static ConnectedDatabase mockDatabase = mock(ConnectedDatabase.class);
  private static TagQueryProvider mockTagQueryProvider = mock(TagQueryProvider.class);
  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SqlConnector connector = new SqlConnector(mockDatabase, mockTagQueryProvider);

  @AfterEach
  void tearDown() {
    reset(mockDatabase, mockTagQueryProvider, mockApiClient, mockConnectorStore);
  }

  @Test
  void init_without_error() {
    connector.init(new ConnectorModule(mockApiClient, mockConnectorStore));
  }

  @Test
  void postTime_is_unsupported() {
    assertThrows(UnsupportedOperationException.class, () ->
        connector.postTime(mock(Request.class), mock(TimeGroup.class))
    );
  }

  @Test
  void isConnectorHealthy() {
    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of());
    when(mockDatabase.isAvailable()).thenReturn(false);
    assertThat(connector.isConnectorHealthy()).isFalse();

    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of());
    when(mockDatabase.isAvailable()).thenReturn(true);
    assertThat(connector.isConnectorHealthy()).isFalse();

    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(new TagQuery("name", "sql")));
    when(mockDatabase.isAvailable()).thenReturn(false);
    assertThat(connector.isConnectorHealthy()).isFalse();

    when(mockTagQueryProvider.getQueries()).thenReturn(ImmutableList.of(new TagQuery("name", "sql")));
    when(mockDatabase.isAvailable()).thenReturn(true);
    assertThat(connector.isConnectorHealthy()).isTrue();
  }

  @Test
  void shutdown() {
    connector.shutdown();
    verify(mockDatabase).close();
    verify(mockTagQueryProvider).stopWatching();
  }
}