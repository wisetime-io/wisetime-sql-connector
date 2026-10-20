/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.github.javafaker.Faker;
import io.vavr.control.Try;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimesheetFieldOptionRecordTest {

  private static final Faker FAKER = Faker.instance();

  @Test
  void fluentJdbcMapper_allDataPresent() throws SQLException {
    final Map<String, Object> dataMap = getTestDataMap();
    final ResultSet resultSet = createMockResultSet(dataMap);

    final TimesheetFieldOptionRecord record = TimesheetFieldOptionRecord.fluentJdbcMapper(false).map(resultSet);

    assertThat(record.getCode()).isEqualTo(dataMap.get("code"));
    assertThat(record.getLabel()).isEqualTo(dataMap.get("label"));
    assertThat(record.getDescription()).isEqualTo(dataMap.get("description"));
    assertThat(record.getEnableIfNew()).isEqualTo(dataMap.get("enable_if_new"));
    assertThat(record.getReenableIfArchived()).isEqualTo(dataMap.get("reenable_if_archived"));
    assertThat(record.getSyncMarker()).isNull();
  }

  @Test
  void fluentJdbcMapper_missingOptionalData() throws SQLException {
    final Map<String, Object> dataMap = getTestDataMap();
    dataMap.remove("description");
    dataMap.remove("enable_if_new");
    dataMap.remove("reenable_if_archived");
    final ResultSet resultSet = createMockResultSet(dataMap);

    final TimesheetFieldOptionRecord record = TimesheetFieldOptionRecord.fluentJdbcMapper(false).map(resultSet);

    assertThat(record.getCode()).isEqualTo(dataMap.get("code"));
    assertThat(record.getLabel()).isEqualTo(dataMap.get("label"));
    assertThat(record.getDescription()).isNull();
    assertThat(record.getEnableIfNew()).isFalse();
    assertThat(record.getReenableIfArchived()).isFalse();
  }

  @Test
  void fluentJdbcMapper_withSyncMarker() throws SQLException {
    final Map<String, Object> dataMap = getTestDataMap();
    dataMap.put("sync_marker", FAKER.numerify("sync-marker-###"));
    final ResultSet resultSet = createMockResultSet(dataMap);

    final TimesheetFieldOptionRecord record = TimesheetFieldOptionRecord.fluentJdbcMapper(true).map(resultSet);

    assertThat(record.getSyncMarker()).isEqualTo(dataMap.get("sync_marker"));
  }

  @Test
  void fluentJdbcMapper_withSyncMarkerFlagOff_syncMarkerNotRead() throws SQLException {
    final Map<String, Object> dataMap = getTestDataMap();
    dataMap.put("sync_marker", FAKER.numerify("sync-marker-###"));
    final ResultSet resultSet = createMockResultSet(dataMap);

    final TimesheetFieldOptionRecord record = TimesheetFieldOptionRecord.fluentJdbcMapper(false).map(resultSet);

    assertThat(record.getSyncMarker()).isNull();
  }

  private Map<String, Object> getTestDataMap() {
    return new HashMap<>(Map.of(
        "code", FAKER.numerify("code-###"),
        "label", FAKER.numerify("label-###"),
        "description", FAKER.numerify("description-###"),
        "enable_if_new", true,
        "reenable_if_archived", true));
  }

  private ResultSet createMockResultSet(Map<String, Object> fields) {
    final ResultSet mockResultSet = mock(ResultSet.class);
    fields.forEach((key, value) -> {
      if (value instanceof Boolean) {
        Try.run(() -> doReturn(value).when(mockResultSet).getBoolean(key));
      } else {
        Try.run(() -> doReturn(value).when(mockResultSet).getString(key));
      }
    });
    return mockResultSet;
  }
}
