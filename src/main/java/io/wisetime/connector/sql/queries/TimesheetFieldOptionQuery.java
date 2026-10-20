/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * SQL query that selects the options for a single connected timesheet field.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimesheetFieldOptionQuery {

  private String fieldId;
  private String sql;
  private String initialSyncMarker;
  private List<String> skippedCodes = Collections.emptyList();

  public void enforceValid() {
    Preconditions.checkArgument(StringUtils.isNotEmpty(fieldId),
        "Field ID is required for timesheet field option SQL query");
    Preconditions.checkArgument(StringUtils.isNotEmpty(sql),
        "SQL is required for timesheet field option SQL query for field %s", fieldId);

    final boolean hasSkippedCodesInSql = sql.contains(":skipped_codes");
    final boolean hasSkippedCodesInQuery = CollectionUtils.isNotEmpty(skippedCodes);
    if (hasSkippedCodesInSql) {
      Preconditions.checkArgument(hasSkippedCodesInQuery,
          "Skipped CODE list is required for provided timesheet field option SQL query for field %s.", fieldId);
    }
    if (hasSkippedCodesInQuery) {
      Preconditions.checkArgument(hasSkippedCodesInSql,
          "To skip provided codes your timesheet field option query SQL for field %s must contain "
              + "`:skipped_codes` parameter.", fieldId);
    }
    final boolean hasInitialSyncMarker = StringUtils.isNotEmpty(initialSyncMarker);
    final boolean hasSyncMarkerInSql = hasSyncMarker();
    if (hasSyncMarkerInSql) {
      Preconditions.checkArgument(hasInitialSyncMarker,
          "Sync marker is used in SQL for field %s. Initial sync marker should be defined.", fieldId);
      Preconditions.checkArgument(hasSkippedCodesInQuery,
          "Sync marker is used in SQL for field %s. Query must define skipped codes list and SQL must contain "
              + ":skipped_codes parameter", fieldId);
    }
    if (hasInitialSyncMarker) {
      Preconditions.checkArgument(hasSyncMarkerInSql,
          "Initial sync marker is found while SQL for field %s doesn't contain required 'sync_marker' "
              + "field and/or ':previous_sync_marker' parameter.", fieldId);
    }
  }

  public boolean hasSyncMarker() {
    // ":previous_sync_marker" itself contains "sync_marker", so look for the column outside of the parameter
    return sql.contains(":previous_sync_marker") && sql.replace(":previous_sync_marker", "").contains("sync_marker");
  }

  public static boolean allUniqueFieldIds(Collection<TimesheetFieldOptionQuery> queries) {
    return queries.stream().map(TimesheetFieldOptionQuery::getFieldId).distinct().count() == queries.size();
  }
}
