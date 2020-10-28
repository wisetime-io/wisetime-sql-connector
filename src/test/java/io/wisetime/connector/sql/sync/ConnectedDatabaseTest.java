/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.github.javafaker.Faker;
import com.zaxxer.hikari.HikariDataSource;
import io.vavr.control.Try;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConnectedDatabaseTest {

  public static Faker FAKER = Faker.instance();

  @Test
  void toTagSyncRecord_allDataPresent() throws SQLException {
    HikariDataSource dataSource = mock(HikariDataSource.class);
    ConnectedDatabase connectedDatabase = new ConnectedDatabase(dataSource);
    Map<String, String> dataMap = getTestDataMap();
    ResultSet resultSet = createMockResultSet(dataMap);
    TagSyncRecord tagSyncRecord = connectedDatabase.toTagSyncRecord(resultSet);
    assertThat(tagSyncRecord.getSyncMarker()).isEqualTo(dataMap.get("sync_marker"));
    assertThat(tagSyncRecord.getTagDescription()).isEqualTo(dataMap.get("tag_description"));
    assertThat(tagSyncRecord.getUrl()).isEqualTo(dataMap.get("url"));
    assertThat(tagSyncRecord.getTagName()).isEqualTo(dataMap.get("tag_name"));
    assertThat(tagSyncRecord.getTagMetadata()).isEqualTo(dataMap.get("tag_metadata"));
    assertThat(tagSyncRecord.getId()).isEqualTo(dataMap.get("id"));
    assertThat(tagSyncRecord.getAdditionalKeyword()).isEqualTo(dataMap.get("additional_keyword"));
  }

  @Test
  void toTagSyncRecord_missingOptionalData() throws SQLException {
    HikariDataSource dataSource = mock(HikariDataSource.class);
    ConnectedDatabase connectedDatabase = new ConnectedDatabase(dataSource);
    Map<String, String> dataMap = getTestDataMap();
    dataMap.remove("tag_metadata");
    dataMap.remove("url");
    ResultSet resultSet = createMockResultSet(dataMap);
    TagSyncRecord tagSyncRecord = connectedDatabase.toTagSyncRecord(resultSet);
    assertThat(tagSyncRecord.getSyncMarker()).isEqualTo(dataMap.get("sync_marker"));
    assertThat(tagSyncRecord.getTagDescription()).isEqualTo(dataMap.get("tag_description"));
    assertThat(tagSyncRecord.getUrl()).isEqualTo(null);
    assertThat(tagSyncRecord.getTagName()).isEqualTo(dataMap.get("tag_name"));
    assertThat(tagSyncRecord.getTagMetadata()).isEqualTo("{}");
    assertThat(tagSyncRecord.getId()).isEqualTo(dataMap.get("id"));
    assertThat(tagSyncRecord.getAdditionalKeyword()).isEqualTo(dataMap.get("additional_keyword"));
  }

  private Map<String, String> getTestDataMap() {
    Map<String, String> dataMap = Map.of("id", FAKER.idNumber().valid(),
        "tag_name", FAKER.team().name(),
        "url", FAKER.internet().url(),
        "tag_metadata", "{ location='" + FAKER.address().country() + "'}",
        "additional_keyword", FAKER.company().name(),
        "tag_description", FAKER.company().industry(),
        "sync_marker", FAKER.idNumber().valid());
    return new HashMap(dataMap);
  }

  private ResultSet createMockResultSet(Map<String, String> fields) {
    ResultSet mockResultSet = mock(ResultSet.class);
    fields.forEach((key, value) -> Try.of(() -> doReturn(value).when(mockResultSet).getString(key)));
    return mockResultSet;
  }


}
