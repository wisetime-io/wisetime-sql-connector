/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class ActivityTypeQueryProviderIntegrationTest {

  @Test
  void getQueries_emptyIfFileNotFound() {
    final ActivityTypeQueryProvider queryProvider = new ActivityTypeQueryProvider(Paths.get("does_not_exist"));
    assertThat(queryProvider.getQueries())
        .as("There is nothing to parse")
        .isEmpty();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void getQueries_correctlyParsed() {
    final String fileLocation = getClass().getClassLoader().getResource("activity_types_sql.yaml").getPath();
    final ActivityTypeQueryProvider queryProvider = new ActivityTypeQueryProvider(Paths.get(fileLocation));
    final List<ActivityTypeQuery> queries = queryProvider.getQueries();

    assertThat(queries.size())
        .as("The activity type query is parsed from YAML")
        .isEqualTo(1);

    final ActivityTypeQuery expectedQuery = new ActivityTypeQuery(
        "SELECT TOP 100"
            + " [ACTIVITYCODE] AS [code],"
            + " [ACTIVITYCODE] AS [sync_marker],"
            + " [ACTIVITYNAME] AS [label]"
            + " [ACTIVITYDESCRIPTION] AS [description]"
            + " FROM [dbo].[TEST_ACTIVITYCODES]"
            + " WHERE [ACTIVITYCODE] NOT IN (:skipped_codes) AND [ACTIVITYCODE] > :previous_sync_marker"
            + " ORDER BY [sync_marker];",
        "0",
        Collections.singletonList("23456"));

    assertThat(queries.get(0))
        .as("The activity type query is correctly parsed from YAML")
        .isEqualTo(expectedQuery);
  }

  @Test
  void getQueries_correctlyParsed_noSkippedCodes_noInitialMarker() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, List.of("sql: SELECT * FROM activitytypes"));

    final List<ActivityTypeQuery> queries = new ActivityTypeQueryProvider(path).getQueries();

    assertThat(queries.size())
        .as("The activity type query is parsed from YAML")
        .isEqualTo(1);

    final ActivityTypeQuery expectedQuery = new ActivityTypeQuery(
        "SELECT * FROM activitytypes",
        null,
        Collections.emptyList());

    assertThat(queries.get(0))
        .as("The activity type query is correctly parsed from YAML")
        .isEqualTo(expectedQuery);
  }

  @Test
  void getQueries_fail_multipleQueries() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_multiple_queries", ".yaml");
    Files.write(path, List.of(
        "sql: SELECT 1",
        "---",
        "sql: SELECT 2"
    ));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At most one");
  }

  @Test
  void getQueries_fail_emptyRequiredField() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_empty", ".yaml");
    Files.write(path, List.of(
        "skippedCodes: [0]",
        "sql: "
    ));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL is required");
  }

  @Test
  void getQueries_fail_missingRequiredField() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_empty", ".yaml");
    Files.write(path, List.of("skippedCodes: [0]"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL is required");
  }

  @Test
  void getQueries_fail_missingRequiredSkippedCodes() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, List.of("sql: SELECT * FROM activitytypes WHERE code NOT IN (:skipped_codes)"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Skipped CODE list is required");
  }

  @Test
  void getQueries_fail_missingRequiredSkippedCodesParameter() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, List.of(
        "skippedCodes: [1]",
        "sql: Select 1" // sql doesn't use :skipped_codes parameter while skippedCodes are provided
    ));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL must contain `:skipped_codes` parameter");
  }

  @Test
  void getQueries_fail_missingRequiredInitialSyncMarker() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, List.of("sql: SELECT sync_marker FROM activitytypes WHERE sync_marker > (:previous_sync_marker)"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Initial sync marker should be defined");
  }

  @Test
  void getQueries_fail_missingRequiredSyncMarkerParameters() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, List.of("initialSyncMarker: 0", "sql: SELECT * FROM activitytypes"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL doesn't contain required");
  }

  /**
   * This test is slow. It takes tens of seconds to run.
   * It relies on filesystem notifications, which can take several seconds before firing.
   */
  @Test
  @Disabled("This is VERY slow. Run manually.")
  @SuppressWarnings("unchecked")
  void getQueries_file_watch() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_deletes", ".yaml");
    final QueryProvider.Listener<ActivityTypeQuery> listener = mock(QueryProvider.Listener.class);
    final ActivityTypeQueryProvider queryProvider = new ActivityTypeQueryProvider(path);
    queryProvider.setListener(listener);
    final Duration timeout = Duration.ofSeconds(10);

    // Wait for start file watching
    Thread.sleep(100);

    // Verify file update
    final List<ActivityTypeQuery> expectedQueriesUpdate = List.of(
        new ActivityTypeQuery("SELECT 'activitycodes'", null, Collections.emptyList()));
    Files.write(path, List.of("sql: SELECT 'activitycodes'"));
    queryProvider.waitForQueryChange(expectedQueriesUpdate, timeout);
    assertThat(queryProvider.isHealthy()).isTrue();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesUpdate);
    reset(listener);

    // Verify file deletion
    final List<ActivityTypeQuery> expectedQueriesDeletion = List.of();
    Files.delete(path);
    queryProvider.waitForQueryChange(expectedQueriesDeletion, timeout);
    assertThat(queryProvider.isHealthy()).isFalse();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesDeletion);
    reset(listener);

    // Verify file creation
    Files.createFile(path);
    Files.write(path, List.of(
        "sql: SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
        "skippedCodes: [123]"
    ));
    queryProvider.waitForQueryChange(List.of(
        new ActivityTypeQuery(
            "SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
            null,
            Collections.singletonList("123"))
    ), timeout);
    assertThat(queryProvider.isHealthy()).isTrue();

    // Verify stop watching
    queryProvider.stop();
    Files.delete(path);
    // Watching is stopped, no queries update (expect queries from the previous step)
    queryProvider.waitForQueryChange(List.of(
        new ActivityTypeQuery(
            "SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
            null,
            Collections.singletonList("123"))
    ), timeout);
    assertThat(queryProvider.isHealthy()).isFalse();
  }

}
