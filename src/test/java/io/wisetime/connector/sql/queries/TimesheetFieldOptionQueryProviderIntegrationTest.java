/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimesheetFieldOptionQueryProviderIntegrationTest {

  @Test
  void getQueries_emptyIfFileNotFound() {
    final TimesheetFieldOptionQueryProvider queryProvider =
        new TimesheetFieldOptionQueryProvider(Paths.get("does_not_exist"));
    assertThat(queryProvider.getQueries())
        .as("There is nothing to parse")
        .isEmpty();
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void getQueries_correctlyParsed() {
    final String fileLocation = getClass().getClassLoader().getResource("timesheet_field_option_sql.yaml").getPath();
    final TimesheetFieldOptionQueryProvider queryProvider =
        new TimesheetFieldOptionQueryProvider(Paths.get(fileLocation));
    final List<TimesheetFieldOptionQuery> queries = queryProvider.getQueries();

    assertThat(queries)
        .as("One query document per connected field is parsed from YAML")
        .containsExactly(
            new TimesheetFieldOptionQuery(
                "matter-type",
                "SELECT [CODE] AS [code], [DESCRIPTION] AS [label] FROM [dbo].[MATTER_TYPE];",
                null, Collections.emptyList()),
            new TimesheetFieldOptionQuery(
                "practice-area",
                "SELECT [CODE] AS [code], [DESCRIPTION] AS [label] FROM [dbo].[PRACTICE_AREA];",
                null, Collections.emptyList()));
  }

  @Test
  void getQueries_correctlyParsed_withSyncMarker() throws Exception {
    final Path path = Files.createTempFile("timesheet_field_option_query_test_sync_marker", ".yaml");
    Files.write(path, List.of(
        "fieldId: matter-type",
        "initialSyncMarker: 0",
        "skippedCodes:",
        "  - 123",
        "sql: SELECT [CODE] AS [code], [CODE] AS [sync_marker] FROM [dbo].[MATTER_TYPE]"
            + " WHERE [CODE] NOT IN (:skipped_codes) AND [CODE] > :previous_sync_marker ORDER BY [sync_marker]"
    ));

    final List<TimesheetFieldOptionQuery> queries = new TimesheetFieldOptionQueryProvider(path).getQueries();

    assertThat(queries).containsExactly(new TimesheetFieldOptionQuery(
        "matter-type",
        "SELECT [CODE] AS [code], [CODE] AS [sync_marker] FROM [dbo].[MATTER_TYPE]"
            + " WHERE [CODE] NOT IN (:skipped_codes) AND [CODE] > :previous_sync_marker ORDER BY [sync_marker]",
        "0",
        List.of("123")));
  }

  @Test
  void getQueries_fail_duplicateFieldIds() throws Exception {
    final Path path = Files.createTempFile("timesheet_field_option_query_test_duplicate", ".yaml");
    Files.write(path, List.of(
        "fieldId: field-1",
        "sql: SELECT 1",
        "---",
        "fieldId: field-1",
        "sql: SELECT 2"
    ));
    assertThatThrownBy(() -> new TimesheetFieldOptionQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique field IDs");
  }

  @Test
  void getQueries_fail_missingFieldId() throws Exception {
    final Path path = Files.createTempFile("timesheet_field_option_query_test_no_field_id", ".yaml");
    Files.write(path, List.of("sql: SELECT 1"));
    assertThatThrownBy(() -> new TimesheetFieldOptionQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field ID is required");
  }

  @Test
  void getQueries_fail_missingSql() throws Exception {
    final Path path = Files.createTempFile("timesheet_field_option_query_test_no_sql", ".yaml");
    Files.write(path, List.of("fieldId: field-1"));
    assertThatThrownBy(() -> new TimesheetFieldOptionQueryProvider(path))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SQL is required");
  }
}
