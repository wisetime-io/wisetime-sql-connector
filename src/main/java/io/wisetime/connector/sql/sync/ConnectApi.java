/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import io.vavr.control.Try;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import io.wisetime.generated.connect.ActivityType;
import io.wisetime.generated.connect.SyncActivityTypesRequest;
import io.wisetime.generated.connect.SyncSession;
import io.wisetime.generated.connect.SyncTimesheetFieldOptionsRequest;
import io.wisetime.generated.connect.TimesheetField;
import io.wisetime.generated.connect.TimesheetFieldOption;
import io.wisetime.generated.connect.UpsertTagRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

  /**
   * Upserts the tags that can be converted to a request. A tag with invalid attributes is left out so it doesn't hold
   * up the others. Its reason is logged straight away, and its id is returned so that the caller can report the run as
   * failed once everything else is synced.
   */
  public List<String> upsertWiseTimeTags(Collection<TagSyncRecord> tagSyncRecords) {
    final List<UpsertTagRequest> requests = new ArrayList<>();
    final List<String> failedTagIds = new ArrayList<>();
    for (TagSyncRecord tagSyncRecord : tagSyncRecords) {
      try {
        requests.add(toUpsertTagRequest(tagSyncRecord, tagUpsertPath));
      } catch (InvalidTagAttributesException e) {
        log.warn("Tag id {} was left out of the sync: {}", tagSyncRecord.getId(), e.getMessage());
        failedTagIds.add(tagSyncRecord.getId());
      }
    }
    if (!requests.isEmpty()) {
      Try.run(() -> apiClient.tagUpsertBatch(requests))
          .onFailure(ioe -> {
            throw new RuntimeException(ioe);
          });
    }
    return failedTagIds;
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

  public List<TimesheetField> listTimesheetFields() {
    try {
      return apiClient.listTimesheetFields();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String startTimesheetFieldOptionsSyncSession(String fieldId) {
    try {
      return apiClient.timesheetFieldOptionsStartSyncSession(fieldId).getSyncSessionId();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void completeTimesheetFieldOptionsSyncSession(String fieldId, String syncSessionId) {
    completeTimesheetFieldOptionsSyncSession(fieldId, syncSessionId, noop);
  }

  public void completeTimesheetFieldOptionsSyncSession(String fieldId, String syncSessionId, Runnable onInvalidSession) {
    sessionApiCall(
        () -> apiClient.timesheetFieldOptionsCompleteSyncSession(fieldId, new SyncSession().syncSessionId(syncSessionId)),
        onInvalidSession);
  }

  public void syncTimesheetFieldOptions(
      String fieldId, Collection<TimesheetFieldOptionRecord> timesheetFieldOptionRecords, String sessionId) {
    syncTimesheetFieldOptions(fieldId, timesheetFieldOptionRecords, sessionId, noop);
  }

  public void syncTimesheetFieldOptions(
      String fieldId,
      Collection<TimesheetFieldOptionRecord> timesheetFieldOptionRecords,
      String sessionId,
      Runnable onInvalidSession) {
    final List<TimesheetFieldOption> timesheetFieldOptions = timesheetFieldOptionRecords.stream()
        .map(record -> new TimesheetFieldOption()
            .code(record.getCode())
            .label(record.getLabel())
            .description(record.getDescription())
            .enableIfNew(record.getEnableIfNew())
            .reenableIfArchived(record.getReenableIfArchived()))
        .collect(Collectors.toList());

    sessionApiCall(
        () -> apiClient.syncTimesheetFieldOptions(fieldId, new SyncTimesheetFieldOptionsRequest()
            .syncSessionId(sessionId)
            .options(timesheetFieldOptions)),
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

    // Connect API replaces the tag's whole attribute set on upsert, while null leaves the existing attributes unchanged.
    // Set null explicitly: the generated request defaults to an empty map, which would remove them.
    request.attributes(tagSyncRecord.getTagAttributes()
        .map(attributes -> parseAttributes(tagSyncRecord.getId(), attributes))
        .orElse(null));

    if (isNotEmpty(tagSyncRecord.getTagDescription())) {
      // Only overwrite existing description if we are given a new one
      request.description(tagSyncRecord.getTagDescription());
    }
    return request;
  }

  /**
   * Fails with a message naming the tag when the attributes are not a valid JSON object, have blank names or a number
   * out of range, instead of a bare Gson exception. The tag is left out of the sync, so its attributes never go stale
   * silently.
   */
  private static Map<String, Object> parseAttributes(String tagId, String attributes) {
    final JsonElement json;
    try {
      json = JsonParser.parseString(attributes);
    } catch (JsonParseException e) {
      throw new InvalidTagAttributesException(
          "tag_attributes for tag id " + tagId + " is not valid JSON: " + e.getMessage(), e);
    }
    if (!json.isJsonObject()) {
      throw new InvalidTagAttributesException(
          "tag_attributes for tag id " + tagId + " must be a JSON object but was: " + json);
    }
    try {
      return toMap(json.getAsJsonObject());
    } catch (IllegalArgumentException e) {
      // Blank names, and numbers that Gson refuses to convert (NumberFormatException), see toValue
      throw new InvalidTagAttributesException(
          "tag_attributes for tag id " + tagId + " is invalid: " + e.getMessage(), e);
    }
  }

  /**
   * Attributes are a free-form JSON object, so arrays, numbers, booleans and nested objects keep their JSON type.
   * A NULL value (for example from JSON_BUILD_OBJECT) means the attribute has no value, so it is left out at any depth.
   * Sending it would make Connect API reject the whole batch of tags.
   * Blank attribute names, at any depth, are rejected because Connect API would reject them too.
   */
  private static Map<String, Object> toMap(JsonObject json) {
    Map<String, Object> result = new HashMap<>();
    json.entrySet().forEach(entry -> {
      if (entry.getKey().isBlank()) {
        throw new IllegalArgumentException("attribute names must not be blank");
      }
      if (!entry.getValue().isJsonNull()) {
        result.put(entry.getKey(), toValue(entry.getValue()));
      }
    });
    return result;
  }

  private static Object toValue(JsonElement json) {
    if (json.isJsonObject()) {
      return toMap(json.getAsJsonObject());
    }
    if (json.isJsonArray()) {
      List<Object> list = new ArrayList<>();
      json.getAsJsonArray().forEach(item -> {
        if (!item.isJsonNull()) {
          list.add(toValue(item));
        }
      });
      return list;
    }
    JsonPrimitive primitive = json.getAsJsonPrimitive();
    if (primitive.isBoolean()) {
      return primitive.getAsBoolean();
    }
    if (primitive.isNumber()) {
      // Gson bounds the number length and exponent, and throws NumberFormatException beyond that, so a huge exponent
      // can't turn into a giant BigInteger here
      BigDecimal number = primitive.getAsBigDecimal();
      return number.stripTrailingZeros().scale() <= 0 ? (Object) number.toBigInteger() : number;
    }
    return primitive.getAsString();
  }

  private static class InvalidTagAttributesException extends RuntimeException {

    InvalidTagAttributesException(String message) {
      super(message);
    }

    InvalidTagAttributesException(String message, Throwable cause) {
      super(message, cause);
    }
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
