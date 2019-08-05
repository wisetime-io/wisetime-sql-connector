/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.generated.connect.UpsertTagRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author shane.xie
 */
public class ConnectApi {

  private ApiClient apiClient;
  private String tagUpsertPath;

  public ConnectApi(final ApiClient apiClient) {
    this.apiClient = apiClient;

    tagUpsertPath = RuntimeConfig.getString(SqlConnectorConfigKey.TAG_UPSERT_PATH)
        .orElseThrow(() -> new RuntimeException("Missing required TAG_UPSERT_PATH configuration"));
    Preconditions.checkArgument(tagUpsertPath.startsWith("/"));
  }

  public void upsertWiseTimeTags(Collection<TagSyncRecord> tagSyncRecords) {
    final List<UpsertTagRequest> requests = tagSyncRecords.stream()
        .map(tagSyncRecord -> toUpsertTagRequest(tagSyncRecord, tagUpsertPath))
        .collect(Collectors.toList());
    if (!requests.isEmpty()) {
      try {
        apiClient.tagUpsertBatch(requests);
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }

  private UpsertTagRequest toUpsertTagRequest(final TagSyncRecord tagSyncRecord, final String path) {
    final UpsertTagRequest request = new UpsertTagRequest()
        .name(tagSyncRecord.getTagName())
        .additionalKeywords(ImmutableList.of(tagSyncRecord.getAdditionalKeyword()))
        .excludeTagNameKeyword(true)
        .path(path);

    if (isNotEmpty(tagSyncRecord.getTagDescription())) {
      // Only overwrite existing description if we are given a new one
      request.description(tagSyncRecord.getTagDescription());
    }
    return request;
  }
}
