/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */


package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yehor.lashkul
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityTypeQuery {

  private String sql;
  private String initialSyncMarker;
  private List<String> skippedCodes = Collections.emptyList();

  public void enforceValid() {
    Preconditions.checkArgument(StringUtils.isNotEmpty(sql),
        "SQL is required for activity type SQL query");
    final boolean hasSkippedCodesInSql = sql.contains(":skipped_codes");
    final boolean hasSkippedCodesInQuery = CollectionUtils.isNotEmpty(skippedCodes);
    if (hasSkippedCodesInSql) {
      Preconditions.checkArgument(hasSkippedCodesInQuery,
          "Skipped CODE list is required for provided activity type SQL query.");
    }
    if (hasSkippedCodesInQuery) {
      Preconditions.checkArgument(hasSkippedCodesInSql,
          "To skip provided codes your activity type query SQL must contain `:skipped_codes` parameter.");
    }
    final boolean hasInitialSyncMarker = StringUtils.isNotEmpty(initialSyncMarker);
    final boolean hasSyncMarkerInSql = hasSyncMarker();
    if (hasSyncMarkerInSql) {
      Preconditions.checkArgument(hasInitialSyncMarker,
          "Sync marker is used in SQL. Initial sync marker should be defined.");
    }
    if (hasInitialSyncMarker) {
      Preconditions.checkArgument(hasSyncMarkerInSql,
          "Initial sync marker is found while SQL doesn't contain required 'sync_marker' "
              + "field and/or ':previous_sync_marker' parameter.");
    }
  }

  public boolean hasSyncMarker() {
    return sql.contains("sync_marker") && sql.contains(":previous_sync_marker");
  }
}
