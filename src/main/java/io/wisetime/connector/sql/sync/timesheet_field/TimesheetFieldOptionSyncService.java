/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field;

import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;

public interface TimesheetFieldOptionSyncService {

  void performTimesheetFieldOptionUpdate(TimesheetFieldOptionQuery timesheetFieldOptionQuery);

  void performTimesheetFieldOptionUpdateSlowLoop(TimesheetFieldOptionQuery timesheetFieldOptionQuery);
}
