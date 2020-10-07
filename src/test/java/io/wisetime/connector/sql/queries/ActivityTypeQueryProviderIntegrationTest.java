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

import com.google.common.collect.ImmutableList;
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
  void getQueries_correctlyParsed() {
    final String fileLocation = getClass().getClassLoader().getResource("activity_types_sql.yaml").getPath();
    final ActivityTypeQueryProvider queryProvider = new ActivityTypeQueryProvider(Paths.get(fileLocation));
    final List<ActivityTypeQuery> queries = queryProvider.getQueries();

    assertThat(queries.size())
        .as("The activity type query is parsed from YAML")
        .isEqualTo(1);

    final ActivityTypeQuery expectedQuery = new ActivityTypeQuery(
        "SELECT"
            + " [ACTIVITYCODE] AS [code],"
            + " [ACTIVITYNAME] AS [description]"
            + " FROM [dbo].[TEST_ACTIVITYCODES]"
            + " WHERE [ACTIVITYCODE] NOT IN (:skipped_codes)"
            + " ORDER BY [code];",
        Collections.singletonList("23456"));

    assertThat(queries.get(0))
        .as("The activity type query is correctly parsed from YAML")
        .isEqualTo(expectedQuery);
  }

  @Test
  void getQueries_correctlyParsed_noSkippedCodes() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, ImmutableList.of("sql: SELECT * FROM activitytypes"));

    final List<ActivityTypeQuery> queries = new ActivityTypeQueryProvider(path).getQueries();

    assertThat(queries.size())
        .as("The activity type query is parsed from YAML")
        .isEqualTo(1);

    final ActivityTypeQuery expectedQuery = new ActivityTypeQuery(
        "SELECT * FROM activitytypes",
        Collections.emptyList());

    assertThat(queries.get(0))
        .as("The activity type query is correctly parsed from YAML")
        .isEqualTo(expectedQuery);
  }

  @Test
  void getQueries_fail_multipleQueries() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_multiple_queries", ".yaml");
    Files.write(path, ImmutableList.of(
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
    Files.write(path, ImmutableList.of(
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
    Files.write(path, ImmutableList.of("skippedCodes: [0]"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL is required");
  }

  @Test
  void getQueries_fail_missingRequiredSkippedCodes() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, ImmutableList.of("sql: SELECT * FROM activitytypes WHERE code NOT IN (:skipped_codes)"));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Skipped CODE list is required");
  }

  @Test
  void getQueries_fail_missingRequiredSkippedCodesParameter() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_no_field", ".yaml");
    Files.write(path, ImmutableList.of(
        "skippedCodes: [1]",
        "sql: Select 1" // sql doesn't use :skipped_codes parameter while skippedCodes are provided
    ));
    assertThatThrownBy(() -> new ActivityTypeQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL must contain `:skipped_codes` parameter");
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
    final List<ActivityTypeQuery> expectedQueriesUpdate = ImmutableList.of(
        new ActivityTypeQuery("SELECT 'activitycodes'", Collections.emptyList()));
    Files.write(path, ImmutableList.of("sql: SELECT 'activitycodes'"));
    queryProvider.waitForQueryChange(expectedQueriesUpdate, timeout);
    assertThat(queryProvider.isHealthy()).isTrue();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesUpdate);
    reset(listener);

    // Verify file deletion
    final List<ActivityTypeQuery> expectedQueriesDeletion = ImmutableList.of();
    Files.delete(path);
    queryProvider.waitForQueryChange(expectedQueriesDeletion, timeout);
    assertThat(queryProvider.isHealthy()).isFalse();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesDeletion);
    reset(listener);

    // Verify file creation
    Files.createFile(path);
    Files.write(path, ImmutableList.of(
        "sql: SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
        "skippedCodes: [123]"
    ));
    queryProvider.waitForQueryChange(ImmutableList.of(
        new ActivityTypeQuery(
            "SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
            Collections.singletonList("123"))
    ), timeout);
    assertThat(queryProvider.isHealthy()).isTrue();

    // Verify stop watching
    queryProvider.stop();
    Files.delete(path);
    // Watching is stopped, no queries update (expect queries from the previous step)
    queryProvider.waitForQueryChange(ImmutableList.of(
        new ActivityTypeQuery(
            "SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
            Collections.singletonList("123"))
    ), timeout);
    assertThat(queryProvider.isHealthy()).isFalse();
  }

}
