/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.marker;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
class TimesheetFieldOptionSyncWithMarkerStore {

  private static final String CODES_DELIMITER = "@@@";

  private final ConnectorStore connectorStore;
  private final String keySpace;

  public TimesheetFieldOptionSyncWithMarkerStore(ConnectorStore connectorStore) {
    this(connectorStore, "");
  }

  void markSyncPosition(TimesheetFieldOptionQuery query, List<TimesheetFieldOptionRecord> options) {
    Preconditions.checkArgument(options.size() > 0, "timesheet field options can't be empty");

    final String previousMarker = getSyncMarker(query);
    final String latestMarker = options.get(options.size() - 1).getSyncMarker();
    connectorStore.putString(syncMarkerKey(query), latestMarker);

    final Stream<String> latestBatchCodes = options.stream()
        .filter(option -> option.getSyncMarker().equals(latestMarker))
        .map(TimesheetFieldOptionRecord::getCode);
    // Rows sharing one marker value can span several batches: while the marker doesn't move, keep the codes of
    // the earlier batches too, otherwise they are returned again and the sync alternates between batches forever.
    final Stream<String> codesToSkip = latestMarker.equals(previousMarker)
        ? Stream.concat(getLastSyncedCodes(query).stream(), latestBatchCodes)
        : latestBatchCodes;
    connectorStore.putString(lastSyncedCodesKey(query),
        codesToSkip.distinct().collect(Collectors.joining(CODES_DELIMITER)));
  }

  void resetSyncPosition(TimesheetFieldOptionQuery query) {
    connectorStore.putString(syncMarkerKey(query), query.getInitialSyncMarker());
    connectorStore.putString(syncSessionKey(query), "");
    connectorStore.putString(lastSyncedCodesKey(query), "");
  }

  String getSyncMarker(TimesheetFieldOptionQuery query) {
    return connectorStore.getString(syncMarkerKey(query))
        .filter(StringUtils::isNotEmpty)
        .orElse(query.getInitialSyncMarker());
  }

  List<String> getLastSyncedCodes(TimesheetFieldOptionQuery query) {
    return connectorStore.getString(lastSyncedCodesKey(query))
        .filter(StringUtils::isNotEmpty)
        .map(refs -> refs.split(CODES_DELIMITER))
        .map(Arrays::asList)
        .orElse(List.of());
  }

  void saveSyncSession(TimesheetFieldOptionQuery query, String syncSessionId) {
    connectorStore.putString(syncSessionKey(query), syncSessionId);
  }

  Optional<String> getSyncSession(TimesheetFieldOptionQuery query) {
    return connectorStore.getString(syncSessionKey(query))
        .filter(StringUtils::isNotEmpty);
  }

  @VisibleForTesting
  String syncMarkerKey(final TimesheetFieldOptionQuery query) {
    return keySpace + query.hashCode() + "_timesheet_field_option_sync_marker";
  }

  @VisibleForTesting
  String lastSyncedCodesKey(final TimesheetFieldOptionQuery query) {
    return keySpace + query.hashCode() + "_timesheet_field_option_last_sync_codes";
  }

  @VisibleForTesting
  String syncSessionKey(final TimesheetFieldOptionQuery query) {
    return keySpace + query.hashCode() + "_timesheet_field_option_sync_session";
  }
}
