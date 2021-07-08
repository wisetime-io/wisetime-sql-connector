/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.connector.sql.queries.ActivityTypeQueryProvider;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.generated.connect.TimeGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Request;

/**
 * @author shane.xie
 */
class SqlConnectorTest {

  private static ConnectedDatabase mockDatabase = mock(ConnectedDatabase.class);
  private static TagQueryProvider mockTagQueryProvider = mock(TagQueryProvider.class);
  private static ActivityTypeQueryProvider mockActivityTypeQueryProvider = mock(ActivityTypeQueryProvider.class);
  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private static SqlConnector connector;

  @BeforeAll
  static void setUp() {
    RuntimeConfig.setProperty(SqlConnectorConfigKey.TAG_UPSERT_PATH, "/connector/");
    connector = new SqlConnector(mockDatabase, mockTagQueryProvider, mockActivityTypeQueryProvider);

    // check that connector has set a listener to the tag query provider
    verify(mockTagQueryProvider, times(1)).setListener(any());
    // check that connector has set a listener to the activity type query provider
    verify(mockActivityTypeQueryProvider, times(1)).setListener(any());
  }

  @AfterEach
  void tearDown() {
    reset(mockDatabase, mockTagQueryProvider, mockApiClient, mockConnectorStore);
  }

  @Test
  void init_without_error() {
    connector.init(new ConnectorModule(mockApiClient, mockConnectorStore));
  }

  @Test
  void connectorType_is_unchanged() {
    assertThat(connector.getConnectorType()).isEqualTo("wisetime-sql-connector");
  }

  @Test
  void postTime_is_unsupported() {
    assertThrows(UnsupportedOperationException.class, () ->
        connector.postTime(mock(Request.class), mock(TimeGroup.class))
    );
  }

  @Test
  void isConnectorHealthy() {
    // if at least one query provider is unhealthy or database is not available
    // the whole connector is treated as unhealthy
    when(mockTagQueryProvider.isHealthy()).thenReturn(false);
    when(mockActivityTypeQueryProvider.isHealthy()).thenReturn(true);
    when(mockDatabase.isAvailable()).thenReturn(true);
    assertThat(connector.isConnectorHealthy()).isFalse();

    when(mockTagQueryProvider.isHealthy()).thenReturn(true);
    when(mockActivityTypeQueryProvider.isHealthy()).thenReturn(false);
    when(mockDatabase.isAvailable()).thenReturn(true);
    assertThat(connector.isConnectorHealthy()).isFalse();

    when(mockTagQueryProvider.isHealthy()).thenReturn(true);
    when(mockActivityTypeQueryProvider.isHealthy()).thenReturn(true);
    when(mockDatabase.isAvailable()).thenReturn(false);
    assertThat(connector.isConnectorHealthy()).isFalse();

    // healthy only when both query providers are healthy and database is available
    when(mockTagQueryProvider.isHealthy()).thenReturn(true);
    when(mockActivityTypeQueryProvider.isHealthy()).thenReturn(true);
    when(mockDatabase.isAvailable()).thenReturn(true);
    assertThat(connector.isConnectorHealthy()).isTrue();
  }

  @Test
  void shutdown() {
    connector.shutdown();
    verify(mockDatabase).close();
    verify(mockTagQueryProvider).stop();
  }
}
