/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.generated.connect.UpsertTagRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * @author shane.xie
 */
class ConnectApiTest {

  private static ApiClient mockApiClient = mock(ApiClient.class);
  private static ConnectApi connectApi;

  @BeforeAll
  static void setUp() {
    RuntimeConfig.setProperty(SqlConnectorConfigKey.TAG_UPSERT_PATH, "/Connector/");
    connectApi = new ConnectApi(mockApiClient);
  }

  @AfterEach
  void cleanUp() {
    reset(mockApiClient);
  }

  @Test
  void setup_fails_invalid_path() {
    // Path not set
    RuntimeConfig.rebuild();
    assertThrows(RuntimeException.class, () -> new ConnectApi(mockApiClient));

    // Incorrect path set
    RuntimeConfig.setProperty(SqlConnectorConfigKey.TAG_UPSERT_PATH, "invalid");
    assertThrows(IllegalArgumentException.class, () -> new ConnectApi(mockApiClient));
  }

  @Test
  void upsertWiseTimeTags_sends_correct_requests() throws Exception {
    final TagSyncRecord record1 = randomTagSyncRecord();
    final TagSyncRecord record2 = randomTagSyncRecord();
    final List<TagSyncRecord> tagSyncRecords = ImmutableList.of(record1, record2);

    connectApi.upsertWiseTimeTags(tagSyncRecords);
    ArgumentCaptor<List<UpsertTagRequest>> argument = ArgumentCaptor.forClass(List.class);
    verify(mockApiClient).tagUpsertBatch(argument.capture());

    final UpsertTagRequest request1 = new UpsertTagRequest()
        .name(record1.getTagName())
        .additionalKeywords(ImmutableList.of(record1.getAdditionalKeyword()))
        .description(record1.getTagDescription())
        .excludeTagNameKeyword(true)
        .path("/Connector/");

    final UpsertTagRequest request2 = new UpsertTagRequest()
        .name(record2.getTagName())
        .additionalKeywords(ImmutableList.of(record2.getAdditionalKeyword()))
        .description(record2.getTagDescription())
        .excludeTagNameKeyword(true)
        .path("/Connector/");

    assertThat(argument.getValue())
        .as("Connect API is called with the expected requests")
        .containsExactly(request1, request2);
  }

  @Test
  void upsertWiseTimeTags_should_not_update_description_if_null() throws Exception {
    TagSyncRecord record = randomTagSyncRecord();
    record.setTagDescription(null);

    connectApi.upsertWiseTimeTags(ImmutableList.of(record));

    ArgumentCaptor<List<UpsertTagRequest>> argument = ArgumentCaptor.forClass(List.class);
    verify(mockApiClient).tagUpsertBatch(argument.capture());
    assertThat(argument.getValue().get(0).getDescription())
        .as("Should not ask Connect API to overwrite description if not set")
        .isNull();
  }

  @Test
  void upsertWiseTimeTags_should_not_update_description_if_empty() throws Exception {
    TagSyncRecord record = randomTagSyncRecord();
    record.setTagDescription("");

    connectApi.upsertWiseTimeTags(ImmutableList.of(record));

    ArgumentCaptor<List<UpsertTagRequest>> argument = ArgumentCaptor.forClass(List.class);
    verify(mockApiClient).tagUpsertBatch(argument.capture());
    assertThat(argument.getValue().get(0).getDescription())
        .as("Should not ask Connect API to overwrite description if empty")
        .isNull();
  }
}