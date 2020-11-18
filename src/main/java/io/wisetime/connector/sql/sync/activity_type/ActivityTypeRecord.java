/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.codejargon.fluentjdbc.api.query.Mapper;

/**
 * @author yehor.lashkul
 */
@Data
@RequiredArgsConstructor
public class ActivityTypeRecord {

  @NonNull
  private final String code;

  @NonNull
  private final String label;

  @NonNull
  private final String description;

  private final String syncMarker;

  public ActivityTypeRecord(@NonNull String code, @NonNull String label, @NonNull String description) {
    this.code = code;
    this.label = label;
    this.description = description;
    syncMarker = null;
  }

  public static Mapper<ActivityTypeRecord> fluentJdbcMapper(boolean withSyncMarker) {
    return resultSet -> new ActivityTypeRecord(
        resultSet.getString("code"),
        resultSet.getString("label"),
        resultSet.getString("description"),
        withSyncMarker ? resultSet.getString("sync_marker") : null);
  }
}
