/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.hash;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;

class TimesheetFieldOptionSyncWithHashStore {

  // Control characters that don't occur in option text, so that text moved between neighbouring values, or
  // between neighbouring options, can't produce the same hash input.
  private static final String VALUE_SEPARATOR = "\u0000";
  private static final String OPTION_SEPARATOR = "\u0001";

  private final ConnectorStore connectorStore;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private Function<List<TimesheetFieldOptionRecord>, String> hashFunction;

  TimesheetFieldOptionSyncWithHashStore(ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
    hashFunction = options -> DigestUtils.md5Hex(
        options.stream()
            .map(option -> Stream.of(option.getCode(), option.getLabel(), option.getDescription(),
                    option.getEnableIfNew(), option.getReenableIfArchived())
                .map(String::valueOf)
                .collect(Collectors.joining(VALUE_SEPARATOR)))
            .collect(Collectors.joining(OPTION_SEPARATOR)));
  }

  /**
   * Returns true if there was no sync yet for the field or the provided options hash differs from the previously
   * synced hash.
   */
  boolean isSynced(String fieldId, String optionsHash) {
    return connectorStore.getString(hashKey(fieldId))
        .map(hash -> hash.equals(optionsHash))
        .orElse(false);
  }

  /**
   * Returns true if there was no sync yet for the field or it was more than a {@link Duration} ago.
   */
  boolean lastSyncedOlderThan(String fieldId, Duration duration) {
    return connectorStore.getLong(lastSyncKey(fieldId))
        .map(lastSync -> System.currentTimeMillis() - lastSync > duration.toMillis())
        .orElse(true);
  }

  void markSynced(String fieldId, String optionsHash) {
    connectorStore.putLong(lastSyncKey(fieldId), System.currentTimeMillis());
    connectorStore.putString(hashKey(fieldId), optionsHash);
  }

  String computeHash(List<TimesheetFieldOptionRecord> options) {
    return hashFunction.apply(options);
  }

  @VisibleForTesting
  String hashKey(String fieldId) {
    return "TIMESHEET_FIELD_OPTIONS_HASH_" + fieldId;
  }

  @VisibleForTesting
  String lastSyncKey(String fieldId) {
    return "TIMESHEET_FIELD_OPTIONS_LAST_SYNC_" + fieldId;
  }
}
