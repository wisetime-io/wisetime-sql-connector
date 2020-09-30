/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import lombok.Data;
import lombok.NonNull;
import org.codejargon.fluentjdbc.api.query.Mapper;

/**
 * @author yehor.lashkul
 */
@Data
public class ActivityTypeRecord {

  @NonNull
  private final String code;
  @NonNull
  private final String description;

  public static Mapper<ActivityTypeRecord> fluentJdbcMapper() {
    return resultSet -> new ActivityTypeRecord(
        resultSet.getString("code"),
        resultSet.getString("description"));
  }
}
