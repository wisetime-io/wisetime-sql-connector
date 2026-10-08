/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.hash;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionSyncService;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects changes to a connected timesheet field's options using hashing, and syncs the full option list to WiseTime
 * whenever the hash changes (or once a day regardless, to defend against hash collisions).
 */
@Slf4j
public class TimesheetFieldOptionSyncWithHashService implements TimesheetFieldOptionSyncService {

  private final ConnectApi connectApi;
  private final ConnectedDatabase database;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TimesheetFieldOptionSyncWithHashStore timesheetFieldOptionSyncStore;

  public TimesheetFieldOptionSyncWithHashService(
      ConnectorStore connectorStore,
      ConnectApi connectApi,
      ConnectedDatabase database) {
    timesheetFieldOptionSyncStore = new TimesheetFieldOptionSyncWithHashStore(connectorStore);
    this.connectApi = connectApi;
    this.database = database;
  }

  @Override
  public void performTimesheetFieldOptionUpdate(TimesheetFieldOptionQuery query) {
    final List<TimesheetFieldOptionRecord> options = database.getTimesheetFieldOptions(query);
    final String optionsHash = timesheetFieldOptionSyncStore.computeHash(options);
    final boolean isSynced = timesheetFieldOptionSyncStore.isSynced(query.getFieldId(), optionsHash);
    final boolean syncedMoreThanDayAgo =
        timesheetFieldOptionSyncStore.lastSyncedOlderThan(query.getFieldId(), Duration.ofDays(1));

    // We sync options once a day even if they already were synced
    // In such a manner, we defend against hash collisions
    if (!isSynced || syncedMoreThanDayAgo) {
      final String syncSessionId = connectApi.startTimesheetFieldOptionsSyncSession(query.getFieldId());
      log.info("Sending {} timesheet field options for field {} to sync", options.size(), query.getFieldId());
      connectApi.syncTimesheetFieldOptions(query.getFieldId(), options, syncSessionId);
      connectApi.completeTimesheetFieldOptionsSyncSession(query.getFieldId(), syncSessionId);
      timesheetFieldOptionSyncStore.markSynced(query.getFieldId(), optionsHash);
    }
  }

  @Override
  public void performTimesheetFieldOptionUpdateSlowLoop(TimesheetFieldOptionQuery query) {
    log.debug("There is no slow loop for timesheet field option sync using hashing. Skipping...");
  }
}
