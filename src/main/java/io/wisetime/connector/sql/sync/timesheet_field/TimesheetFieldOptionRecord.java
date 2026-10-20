/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field;

import io.vavr.control.Try;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.codejargon.fluentjdbc.api.query.Mapper;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class TimesheetFieldOptionRecord {

  @NonNull
  private final String code;

  @NonNull
  private final String label;

  private final String description;
  private final Boolean enableIfNew;
  private final Boolean reenableIfArchived;
  private final String syncMarker;

  public static Mapper<TimesheetFieldOptionRecord> fluentJdbcMapper(boolean withSyncMarker) {
    return resultSet -> TimesheetFieldOptionRecord.builder()
        .code(resultSet.getString("code"))
        .label(resultSet.getString("label"))
        .description(Try.of(() -> resultSet.getString("description")).getOrNull())
        .enableIfNew(Try.of(() -> resultSet.getBoolean("enable_if_new")).getOrElse(false))
        .reenableIfArchived(Try.of(() -> resultSet.getBoolean("reenable_if_archived")).getOrElse(false))
        .syncMarker(withSyncMarker ? resultSet.getString("sync_marker") : null)
        .build();
  }
}
