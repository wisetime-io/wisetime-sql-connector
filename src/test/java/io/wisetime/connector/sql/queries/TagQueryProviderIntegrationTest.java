/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * @author shane.xie
 */
class TagQueryProviderIntegrationTest {

  @Test
  void getTagQueries_empty_if_file_not_found() {
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(Paths.get("does_not_exist"));
    assertThat(tagQueryProvider.getQueries())
        .as("There is nothing to parse")
        .isEmpty();
  }

  @Test
  void getTagQueries_correctly_parsed() {
    final String fileLocation = getClass().getClassLoader().getResource("tag_sql.yaml").getPath();
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(Paths.get(fileLocation));
    final List<TagQuery> tagQueries = tagQueryProvider.getQueries();

    assertThat(tagQueries.size())
        .as("The tag queries are parsed from YAML")
        .isEqualTo(4);

    final TagQuery invalidQuery = new TagQuery("missing-placeholders",
        "SELECT 'missing required fields and parameter placeholders';",
        "0", Collections.singletonList("0"), false);

    assertThat(tagQueries.get(2))
        .as("The tag query is correctly parsed from YAML")
        .isEqualTo(invalidQuery);
  }

  @Test
  void getTagQueries_continuous_resync_defaults_to_true() {
    final String fileLocation = getClass().getClassLoader().getResource("tag_sql.yaml").getPath();
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(Paths.get(fileLocation));
    final List<TagQuery> tagQueries = tagQueryProvider.getQueries();

    final TagQuery invalidQuery = new TagQuery("default-continuous-resync",
        "SELECT 'default continuous resync is true';",
        "0", Collections.singletonList("0"), true);

    assertThat(tagQueries.get(3))
        .as("Continuous resync, if not configured, defaults to true")
        .isEqualTo(invalidQuery);
  }

  @Test
  void getTagQueries_fail_non_unique_queries() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_distinct_queries", ".yaml");
    Files.write(path, ImmutableList.of(
        "name: cases",
        "initialSyncMarker: 0",
        "skippedIds: [0]",
        "sql: SELECT 1",
        "---",
        "name: projects",
        "initialSyncMarker: 0",
        "skippedIds: [0]",
        "sql: SELECT 1"
    ));
    assertThrows(IllegalArgumentException.class, () -> new TagQueryProvider(path));
  }

  @Test
  void getTagQueries_fail_empty_required_field() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_query_names", ".yaml");
    Files.write(path, ImmutableList.of(
        "name: cases",
        "initialSyncMarker: ",
        "skippedIds: [0]",
        "sql: SELECT 1"
    ));
    assertThrows(IllegalArgumentException.class, () -> new TagQueryProvider(path));
  }

  @Test
  void getTagQueries_fail_missing_required_fields() throws Exception {
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
  @SuppressWarnings("unchecked")
  void getTagQueries_file_watch() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_deletes", ".yaml");
    final QueryProvider.Listener<TagQuery> listener = mock(QueryProvider.Listener.class);
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(path);
    tagQueryProvider.setListener(listener);
    final Duration timeout = Duration.ofSeconds(10);

    // Wait for start file watching
    Thread.sleep(100);

    // Verify file update
    final ImmutableList<TagQuery> expectedQueriesUpdate = ImmutableList.of(
        new TagQuery("cases", "SELECT 'cases'", "0", Collections.singletonList("0"), true));
    Files.write(path, ImmutableList.of("name: cases", "sql: SELECT 'cases'", "initialSyncMarker: 0",
        "skippedIds: [0]", "continuousResync: true"));
    tagQueryProvider.waitForQueryChange(expectedQueriesUpdate, timeout);
    assertThat(tagQueryProvider.isHealthy()).isTrue();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesUpdate);
    reset(listener);

    // Verify file deletion
    Files.delete(path);
    final List<TagQuery> expectedQueriesDeletion = ImmutableList.of();
    tagQueryProvider.waitForQueryChange(expectedQueriesDeletion, timeout);
    assertThat(tagQueryProvider.isHealthy()).isFalse();
    verify(listener, times(1)).onQueriesUpdated(expectedQueriesDeletion);
    reset(listener);

    // Verify file creation
    final List<TagQuery> expectedQueriesCreation = ImmutableList.of(
        new TagQuery("keywords", "SELECT 'keywords'", "1", Collections.singletonList("0"), true));
    Files.createFile(path);
    Files.write(path, ImmutableList.of("name: keywords", "sql: SELECT 'keywords'", "initialSyncMarker: 1",
        "skippedIds: [0]", "continuousResync: true"));
    tagQueryProvider.waitForQueryChange(expectedQueriesCreation, timeout);
    assertThat(tagQueryProvider.isHealthy()).isTrue();

    // Verify stop watching
    tagQueryProvider.stop();
    Files.delete(path);
    // Watching is stopped, no queries update (expect queries from the previous step)
    tagQueryProvider.waitForQueryChange(expectedQueriesCreation, timeout);
    assertThat(tagQueryProvider.isHealthy()).isFalse();
  }
}
