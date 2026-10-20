/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.test_docker.ContainerRuntimeSpec;
import io.wisetime.test_docker.DockerLauncher;
import io.wisetime.test_docker.containers.Postgres;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

/**
 * @author dchandler
 */
class ConnectedDatabase_postgresSqlTest {

  private static ConnectedDatabase database;

  @BeforeAll
  static void init() {
    final DockerLauncher launcher = DockerLauncher.instance();
    final ContainerRuntimeSpec containerSpec = launcher.createContainer(new Postgres());
    // stores the base url info from the docker container provided
    final String baseJdbcUrl = String.format("jdbc:postgresql://%s:%d/dockerdb",
        containerSpec.getContainerIpAddress(),
        containerSpec.getRequiredMappedPort(5432));

    final HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(baseJdbcUrl);
    hikariConfig.setUsername("docker");
    hikariConfig.setPassword("docker");
    hikariConfig.setConnectionTimeout(TimeUnit.MINUTES.toMillis(1));
    hikariConfig.setMaximumPoolSize(1);
    final HikariDataSource dataSource = new HikariDataSource(hikariConfig);

    database = new ConnectedDatabase(dataSource);

    final Flyway flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("db_schema/postgres")
        .load();
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
  void getTagsToSync_testCase() {
    final List<TagSyncRecord> tagSyncRecords = database.getTagsToSync(
        "SELECT IRN as id, "
            + " IRN AS tag_name, "
            + " IRN AS additional_keyword, "
            + " TITLE AS tag_description, "
            + " URL AS url,"
            + " IRN AS external_id,"
            + " DATE_UPDATED AS sync_marker "
            + " FROM TEST_CASES "
            + " WHERE DATE_UPDATED >= TO_DATE(:previous_sync_marker, 'YYYY-MM-DD') "
            + " AND IRN NOT IN (:skipped_ids) "
            + " ORDER BY DATE_UPDATED ASC "
            + " LIMIT 50; ",
        "2019-07-21",
        ImmutableList.of("P0436021")
    );

    final TagSyncRecord result = new TagSyncRecord();
    result.setTagName("P0100973");
    result.setTagDescription("Software for connecting SQL database with timekeeping API");
    result.setAdditionalKeyword("P0100973");
    result.setTagMetadata("{}");
    result.setExternalId("P0100973");
    result.setUrl("http://www.google.com");
    result.setId("P0100973");
    result.setSyncMarker("2019-08-06 00:00:00");

    assertThat(tagSyncRecords)
        .as("Query should return one record")
        .hasSize(1)
        .containsExactly(result);
  }

  @Test
  void getTagsToSync_testProjects() {
    final List<TagSyncRecord> tagSyncRecords = database.getTagsToSync(
        "SELECT PRJ_ID AS id, "
            + "  IRN AS tag_name, "
            + "  'FID' || PRJ_ID AS additional_keyword, "
            + "  DESCRIPTION AS tag_description, "
            + "  PRJ_ID AS sync_marker "
            + " FROM TEST_PROJECTS "
            + " WHERE PRJ_ID >= :previous_sync_marker::int "
            + " AND PRJ_ID NOT IN (:skipped_ids::int) "
            + " ORDER BY PRJ_ID ASC "
            + " LIMIT 50; ",

        "80001",
        ImmutableList.of("80001")
    );

    final TagSyncRecord result = new TagSyncRecord();
    result.setTagName("P0070709");
    result.setTagDescription("Response");
    result.setAdditionalKeyword("FID80002");
    result.setTagMetadata("{}");
    result.setId("80002");
    result.setSyncMarker("80002");

    assertThat(tagSyncRecords)
        .as("Query should return one record")
        .containsExactly(result);
  }

  @Test
  void getTagsToSync_testProjectsWithTagMetadata() {
    final List<TagSyncRecord> tagSyncRecords = database.getTagsToSync(
        "SELECT TEST_CASES.IRN as id, "
            + "  TEST_CASES.IRN AS tag_name, "
            + "  TEST_CASES.IRN AS additional_keyword, "
            + "  TITLE AS tag_description, "
            + "  URL AS url,"
            + "  TEST_CASES.IRN as external_id, "
            + "  DATE_UPDATED AS sync_marker, "
            + "  JSON_BUILD_OBJECT("
            + "    'country', TEST_TAG_METADATA.COUNTRY, "
            + "    'location', TEST_TAG_METADATA.LOCATION "
            + "  ) as tag_metadata "
            + " FROM TEST_CASES "
            + " JOIN TEST_TAG_METADATA ON TEST_CASES.IRN = TEST_TAG_METADATA.IRN"
            + " WHERE DATE_UPDATED >= TO_DATE(:previous_sync_marker, 'YYYY-MM-DD') "
            + " AND TEST_CASES.IRN NOT IN (:skipped_ids) "
            + " ORDER BY DATE_UPDATED ASC "
            + " LIMIT 50",
        "2019-07-21",
        ImmutableList.of("P0436021")
    );

    final TagSyncRecord result = new TagSyncRecord();
    result.setTagName("P0100973");
    result.setTagDescription("Software for connecting SQL database with timekeeping API");
    result.setAdditionalKeyword("P0100973");
    result.setTagMetadata("{}");
    result.setUrl("http://www.google.com");
    result.setExternalId("P0100973");
    result.setId("P0100973");
    result.setSyncMarker("2019-08-06 00:00:00");
    result.setTagMetadata("{\"country\" : \"Germany\", \"location\" : \"Berlin\"}");

    assertThat(tagSyncRecords)
        .as("Query should return one record")
        .containsExactly(result);
  }

  @Test
  void getActivityTypes() {
    final ActivityTypeQuery query = new ActivityTypeQuery();
    query.setSql("SELECT ACTIVITYCODE AS code, ACTIVITYNAME AS label, ACTIVITYDESCRIPTION AS description"
            + "  FROM TEST_ACTIVITYCODES");

    assertThat(database.getActivityTypes(query))
        .as("all records should be returned")
        .containsExactlyInAnyOrder(
            new ActivityTypeRecord("12345", "Billable", "Billable description"),
            new ActivityTypeRecord("23456", "Non-Billable", "Non-Billable description"),
            new ActivityTypeRecord("34567", "Default", "Default description"));
  }

  @Test
  void getActivityTypes_withSkippedCodes() {
    final ActivityTypeQuery query = new ActivityTypeQuery(
        "SELECT ACTIVITYCODE AS code, ACTIVITYNAME AS label, ACTIVITYDESCRIPTION AS description"
            + "  FROM TEST_ACTIVITYCODES"
            + "  WHERE ACTIVITYCODE NOT IN (:skipped_codes)",
        null,
        ImmutableList.of("23456")); // code of Non-Billable activity type

    assertThat(database.getActivityTypes(query))
        .as("all records except excluded should be returned")
        .containsExactlyInAnyOrder(
            new ActivityTypeRecord("12345", "Billable", "Billable description"),
            new ActivityTypeRecord("34567", "Default", "Default description"));
  }

  @Test
  void getActivityTypes_withSyncMarker() {
    final ActivityTypeQuery query = new ActivityTypeQuery(
        "SELECT ACTIVITYCODE AS code, ACTIVITYCODE AS sync_marker, ACTIVITYNAME AS label, ACTIVITYDESCRIPTION AS description"
            + "  FROM TEST_ACTIVITYCODES"
            + "  WHERE ACTIVITYCODE >= :previous_sync_marker AND ACTIVITYCODE NOT IN (:skipped_codes)",
        "0",
        List.of("0"));

    assertThat(database.getActivityTypes(query))
        .as("all records should be returned")
        .containsExactlyInAnyOrder(
            new ActivityTypeRecord("12345", "Billable", "Billable description", "12345"),
            new ActivityTypeRecord("23456", "Non-Billable", "Non-Billable description", "23456"),
            new ActivityTypeRecord("34567", "Default", "Default description", "34567"));
  }
}
