/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

    final TagQuery invalidQuery = new TagQuery();
    invalidQuery.setName("invalid");
    invalidQuery.setSql("SELECT 'missing required fields and parameter placeholders';");
    assertThat(tagQueries.get(2))
        .as("The tag query is correctly parsed from YAML")
        .isEqualTo(invalidQuery);
  }

  @Test
  void getQueries_file_watch_detects_updates() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_updates", ".yaml");
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(path);

    assertThat(tagQueryProvider.getQueries())
        .as("The file is empty")
        .isEmpty();

    Files.write(path, ImmutableList.of("name: >", "  sql"));
    Thread.sleep(10000);

    final TagQuery tagQuery = new TagQuery();
    tagQuery.setName("name");
    tagQuery.setSql("sql");

    assertThat(tagQueryProvider.getQueries())
        .as("The file has been updated")
        .containsExactly(tagQuery);

    // TODO(SX): This test is failing
//    Files.delete(path);
  }

  @Test
  void getQueries_file_watch_detects_deletes() {
    // TODO(SX)
  }

  @Test
  void getQueries_file_watch_detects_file_creation() {
    // TODO(SX)
  }

  @Test
  void stopWatching() {
  }
}