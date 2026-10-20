/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.marker;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.DrainRun;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionSyncService;
import java.util.List;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TimesheetFieldOptionSyncWithMarkerService implements TimesheetFieldOptionSyncService {

  private final ConnectApi connectApi;
  private final ConnectedDatabase database;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TimesheetFieldOptionSyncWithMarkerStore timesheetFieldOptionDrainSyncStore;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TimesheetFieldOptionSyncWithMarkerStore timesheetFieldOptionRefreshSyncStore;

  public TimesheetFieldOptionSyncWithMarkerService(
      ConnectorStore connectorStore,
      ConnectApi connectApi,
      ConnectedDatabase database) {
    timesheetFieldOptionDrainSyncStore = new TimesheetFieldOptionSyncWithMarkerStore(connectorStore);
    timesheetFieldOptionRefreshSyncStore = new TimesheetFieldOptionSyncWithMarkerStore(connectorStore, "refresh_");
    this.connectApi = connectApi;
    this.database = database;
  }

  @Override
  public void performTimesheetFieldOptionUpdate(TimesheetFieldOptionQuery query) {
    final String syncMarker = timesheetFieldOptionDrainSyncStore.getSyncMarker(query);
    final boolean isFirstSync = syncMarker.equals(query.getInitialSyncMarker());

    final String syncSessionId = isFirstSync
        ? getOrStartSyncSession(timesheetFieldOptionDrainSyncStore, query)
        : StringUtils.EMPTY;
    new DrainRun<>(
        () -> getUnsyncedRecords(query, timesheetFieldOptionDrainSyncStore),
        newBatch -> {
          connectApi.syncTimesheetFieldOptions(query.getFieldId(), newBatch, syncSessionId,
              () -> timesheetFieldOptionDrainSyncStore.resetSyncPosition(query));
          timesheetFieldOptionDrainSyncStore.markSyncPosition(query, newBatch);
          log.info("New timesheet field option detection for field {}: {} option(s)",
              query.getFieldId(), newBatch.size());
        }
    ).run();

    if (isFirstSync) {
      connectApi.completeTimesheetFieldOptionsSyncSession(query.getFieldId(), syncSessionId);
      // A completed session is invalid server side. Clear it so that a next first sync (e.g. when the source
      // table is still empty and the marker never moved) starts a new session instead of reusing this one.
      timesheetFieldOptionDrainSyncStore.saveSyncSession(query, StringUtils.EMPTY);
      log.info("First run sync is completed within session {} for field {}", syncSessionId, query.getFieldId());
    }
  }

  @Override
  public void performTimesheetFieldOptionUpdateSlowLoop(TimesheetFieldOptionQuery query) {
    final String syncSessionId = getOrStartSyncSession(timesheetFieldOptionRefreshSyncStore, query);
    if (refreshOneBatch(query, syncSessionId)) {
      connectApi.completeTimesheetFieldOptionsSyncSession(query.getFieldId(), syncSessionId,
          () -> timesheetFieldOptionRefreshSyncStore.resetSyncPosition(query));
      // Next refresh batch to start again from the beginning
      log.info("Resetting timesheet field option refresh to start from the beginning for field {}", query.getFieldId());
      timesheetFieldOptionRefreshSyncStore.resetSyncPosition(query);
    }
  }

  private String getOrStartSyncSession(TimesheetFieldOptionSyncWithMarkerStore store, TimesheetFieldOptionQuery query) {
    return store.getSyncSession(query)
        .orElseGet(() -> {
          final String syncSessionId = connectApi.startTimesheetFieldOptionsSyncSession(query.getFieldId());
          store.saveSyncSession(query, syncSessionId);
          return syncSessionId;
        });
  }

  // returns true if batch is empty, assuming slow loop is finished
  private boolean refreshOneBatch(TimesheetFieldOptionQuery query, String syncSessionId) {
    final List<TimesheetFieldOptionRecord> refreshOptions =
        getUnsyncedRecords(query, timesheetFieldOptionRefreshSyncStore);

    if (refreshOptions.isEmpty()) {
      return true;
    }

    connectApi.syncTimesheetFieldOptions(query.getFieldId(), refreshOptions, syncSessionId,
        () -> timesheetFieldOptionRefreshSyncStore.resetSyncPosition(query));
    timesheetFieldOptionRefreshSyncStore.markSyncPosition(query, refreshOptions);
    log.info("Existing timesheet field option refresh for field {}: {} option(s)",
        query.getFieldId(), refreshOptions.size());
    return false;
  }

  private List<TimesheetFieldOptionRecord> getUnsyncedRecords(final TimesheetFieldOptionQuery query,
      final TimesheetFieldOptionSyncWithMarkerStore syncStore) {
    final String syncMarker = syncStore.getSyncMarker(query);
    final List<String> lastSyncedCodesToSkip = syncStore.getLastSyncedCodes(query);
    return database.getTimesheetFieldOptions(query, syncMarker, lastSyncedCodesToSkip);
  }
}
