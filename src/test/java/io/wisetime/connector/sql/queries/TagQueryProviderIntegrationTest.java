/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author shane.xie
 */
class TagQueryProviderIntegrationTest {

  @Test
  void getQueries_empty_if_file_not_found() {
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(Paths.get("does_not_exist"));
    assertThat(tagQueryProvider.getQueries())
        .as("There is nothing to parse")
        .isEmpty();
  }

  @Test
  void getQueries_correctly_parsed() {
    final String fileLocation = getClass().getClassLoader().getResource("tag_sql.yaml").getPath();
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(Paths.get(fileLocation));
    final List tagQueries = tagQueryProvider.getQueries();

    assertThat(tagQueries.size())
        .as("The tag queries are parsed from YAML")
        .isEqualTo(3);

    final TagQuery invalidQuery = new TagQuery("missing-placeholders",
        "SELECT 'missing required fields and parameter placeholders';", "0", "0");

    assertThat(tagQueries.get(2))
        .as("The tag query is correctly parsed from YAML")
        .isEqualTo(invalidQuery);
  }

  @Test
  void getQueries_fail_non_unique_query_names() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_query_names", ".yaml");
    Files.write(path, ImmutableList.of(
        "name: cases",
        "initialSyncMarker: 0",
        "skippedIds: 0",
        "sql: SELECT 1",
        "---",
        "name: cases",
        "initialSyncMarker: 0",
        "skippedIds: 0",
        "sql: SELECT 1"
    ));
    assertThrows(IllegalArgumentException.class, () -> new TagQueryProvider(path));
  }

  @Test
  void getQueries_fail_empty_required_field() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_query_names", ".yaml");
    Files.write(path, ImmutableList.of(
        "name: cases",
        "initialSyncMarker: ",
        "skippedIds: 0",
        "sql: SELECT 1"
    ));
    assertThrows(IllegalArgumentException.class, () -> new TagQueryProvider(path));
  }

  @Test
  void getQueries_fail_missing_required_fields() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_query_names", ".yaml");
    Files.write(path, ImmutableList.of("name: cases"));
    assertThrows(IllegalArgumentException.class, () -> new TagQueryProvider(path));
  }

  /**
   * This test is slow. It takes tens of seconds to run.
   * It relies on filesystem notifications, which can take several seconds before firing.
   */
  @Test
  @Disabled("This is VERY slow. Run manually.")
  void getQueries_file_watch() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_deletes", ".yaml");
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(path);
    final Duration timeout = Duration.ofSeconds(10);

    // Verify file update
    Files.write(path, ImmutableList.of("name: cases", "sql: SELECT 'cases'", "initialSyncMarker: 0", "skippedIds: 0"));
    tagQueryProvider.waitForQueryChange(ImmutableList.of(
        new TagQuery("cases", "SELECT 'cases'", "0", "0")
    ), timeout);

    // Verify file deletion
    Files.delete(path);
    tagQueryProvider.waitForQueryChange(ImmutableList.of(), timeout);

    // Verify file creation
    Files.createFile(path);
    Files.write(path, ImmutableList.of("name: keywords", "sql: SELECT 'keywords'", "initialSyncMarker: 1",
        "skippedIds: 0"));
    tagQueryProvider.waitForQueryChange(ImmutableList.of(
        new TagQuery("keywords", "SELECT 'keywords'", "1", "0")
    ), timeout);
  }
}
