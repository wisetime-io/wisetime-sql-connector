/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vavr.control.Try;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.generated.connect.ActivityType;
import io.wisetime.generated.connect.SyncActivityTypesRequest;
import io.wisetime.generated.connect.SyncSession;
import io.wisetime.generated.connect.UpsertTagRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

/**
 * @author shane.xie
 */
@Slf4j
public class ConnectApi {

  private final Gson gson = new Gson();
  private final Runnable noop = () -> {
  };

  private final ApiClient apiClient;
  private final String tagUpsertPath;

  public ConnectApi(final ApiClient apiClient) {
    this.apiClient = apiClient;

    tagUpsertPath = RuntimeConfig.getString(SqlConnectorConfigKey.TAG_UPSERT_PATH)
        .orElseThrow(() -> new RuntimeException("Missing required TAG_UPSERT_PATH configuration"));
    Preconditions.checkArgument(tagUpsertPath.startsWith("/"), "tag path should start with /");
  }

  public void upsertWiseTimeTags(Collection<TagSyncRecord> tagSyncRecords) {
    final List<UpsertTagRequest> requests = tagSyncRecords.stream()
        .map(tagSyncRecord -> toUpsertTagRequest(tagSyncRecord, tagUpsertPath))
        .collect(Collectors.toList());
    if (!requests.isEmpty()) {
      Try.run(() -> apiClient.tagUpsertBatch(requests))
          .onFailure(ioe -> {
            throw new RuntimeException(ioe);
          });
    }
  }

  public String startSyncSession() {
    try {
      return apiClient.activityTypesStartSyncSession().getSyncSessionId();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void completeSyncSession(String syncSessionId) {
    completeSyncSession(syncSessionId, noop);
  }

  public void completeSyncSession(String syncSessionId, Runnable onInvalidSession) {
    sessionApiCall(
        () -> apiClient.activityTypesCompleteSyncSession(new SyncSession().syncSessionId(syncSessionId)),
        onInvalidSession);
  }

  public void syncActivityTypes(Collection<ActivityTypeRecord> activityTypeRecords, String sessionId) {
    syncActivityTypes(activityTypeRecords, sessionId, noop);
  }

  public void syncActivityTypes(
      Collection<ActivityTypeRecord> activityTypeRecords,
      String sessionId,
      Runnable onInvalidSession) {
    final List<ActivityType> activityTypes = activityTypeRecords.stream()
        .map(activityTypeRecord -> new ActivityType()
            .code(activityTypeRecord.getCode())
            .label(activityTypeRecord.getLabel())
            .description(activityTypeRecord.getDescription()))
        .collect(Collectors.toList());

    sessionApiCall(
        () -> apiClient.syncActivityTypes(new SyncActivityTypesRequest()
            .syncSessionId(sessionId)
            .activityTypes(activityTypes)),
        onInvalidSession);
  }

  private UpsertTagRequest toUpsertTagRequest(final TagSyncRecord tagSyncRecord, final String path) {
    final UpsertTagRequest request = new UpsertTagRequest()
        .name(tagSyncRecord.getTagName())
        .additionalKeywords(ImmutableList.of(tagSyncRecord.getAdditionalKeyword()))
        .url(tagSyncRecord.getUrl())
        .externalId(tagSyncRecord.getExternalId())
        .metadata(
            gson.fromJson(tagSyncRecord.getTagMetadata(), new TypeToken<Map<String, String>>() {
            }.getType()))
        .excludeTagNameKeyword(true)
        .path(path);

    if (isNotEmpty(tagSyncRecord.getTagDescription())) {
      // Only overwrite existing description if we are given a new one
      request.description(tagSyncRecord.getTagDescription());
    }
    return request;
  }

  private void sessionApiCall(SessionVoidApiCall call, Runnable onInvalidSession) {
    try {
      call.invoke();
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        log.warn("Session not found! Clearing sync session and refresh marker to start from the beginning.");
        onInvalidSession.run();
      }
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private interface SessionVoidApiCall {

    void invoke() throws IOException;
  }
}
