/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.wisetime.test_docker.ContainerRuntimeSpec;
import io.wisetime.test_docker.DockerLauncher;
import io.wisetime.test_docker.containers.SqlServer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

/**
 * @author shane.xie
 */
class ConnectedDatabaseTest {

  private static ConnectedDatabase database;

  @BeforeEach
  void setUp() {
    final DockerLauncher launcher = DockerLauncher.instance();
    final SqlServer sqlServer = new SqlServer() {
      @Override
      public String getImageId() {
        return super.getImageId() + ":2017-latest-ubuntu";
      }
    };
    final ContainerRuntimeSpec container = launcher.createContainer(sqlServer);
    final HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(sqlServer.getJdbcUrl(container));
    hikariConfig.setUsername(sqlServer.getUsername());
    hikariConfig.setPassword(sqlServer.getPassword());
    hikariConfig.setConnectionTimeout(TimeUnit.MINUTES.toMillis(1));
    hikariConfig.setMaximumPoolSize(1);
    final HikariDataSource dataSource = new HikariDataSource(hikariConfig);

    database = new ConnectedDatabase(dataSource);

    final Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    flyway.migrate();
  }

  @Test
  void getTagsToSync_invalid_sql() {
    assertThrows(IllegalArgumentException.class, () ->
        database.getTagsToSync("SELECT 1", "", ImmutableList.of())
    );
  }

  @Test
  void isAvailable() {
    assertThat(database.isAvailable())
        .as("Should be able to query the connected database")
        .isTrue();
  }

  @Test
  void getTagsToSync() {
    final List<TagSyncRecord> tagSyncRecords = database.getTagsToSync(
        "SELECT TOP 500 "
            + "[IRN] as [id], "
            + "[IRN] AS [tag_name], "
            + "[IRN] AS [additional_keyword], "
            + "[TITLE] AS [tag_description], "
            + "[DATE_UPDATED] AS [sync_marker] "
            + "FROM [dbo].[TEST_CASES] "
            + "WHERE [DATE_UPDATED] >= :previous_sync_marker "
            + "AND [IRN] NOT IN (:skipped_ids) "
            + "ORDER BY [DATE_UPDATED] ASC;",

        "2019-07-21",
        ImmutableList.of("P0436021")
    );

    final TagSyncRecord result = new TagSyncRecord();
    result.setTagName("P0100973");
    result.setTagDescription("Software for connecting SQL databse with timekeeping API");
    result.setAdditionalKeyword("P0100973");
    result.setId("P0100973");
    result.setSyncMarker("2019-08-06 00:00:00.0");

    assertThat(tagSyncRecords)
        .as("Query should return one record")
        .containsExactly(result);
  }
}