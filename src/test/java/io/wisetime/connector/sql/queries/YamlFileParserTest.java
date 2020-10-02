/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class YamlFileParserTest {

  @Test
  void parse_singleQueryFile() throws Exception {
    final Path path = Files.createTempFile("activity_type_query_test_deletes", ".yaml");
    final YamlFileParser<ActivityTypeQuery> yamlParser = new YamlFileParser<>(ActivityTypeQuery.class);

    Stream.of("\r", "\n", "\r\n")
        .map(lineSeparator ->
            "sql: SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)" + lineSeparator
                + "skippedCodes: [123]")
        .map(content -> writeToFile(path, content))
        .flatMap(file -> parse(yamlParser, file))
        .forEach(parsedQuery -> assertThat(parsedQuery)
            .as("properly parsed")
            .isEqualTo(new ActivityTypeQuery(
                "SELECT 'activitycodes' WHERE codes NOT IN (:skipped_codes)",
                ImmutableList.of("123")
            )));
  }

  @Test
  void parse_multipleQueriesFile() throws Exception {
    final Path path = Files.createTempFile("tag_query_test_deletes", ".yaml");
    final YamlFileParser<TagQuery> yamlParser = new YamlFileParser<>(TagQuery.class);

    Files.write(path, ImmutableList.of(
        "name: cases",
        "initialSyncMarker: 1",
        "skippedIds: [1]",
        "sql: SELECT 1",
        "continuousResync: true",
        "---",
        "name: projects",
        "initialSyncMarker: 2",
        "skippedIds: [2]",
        "sql: SELECT 2",
        "continuousResync: false"
    ));

    assertThat(yamlParser.parse(path).collect(Collectors.toList()))
        .as("file contains 2 queries")
        .hasSize(2)
        .as("properly parsed")
        .containsExactly(
            new TagQuery("cases", "SELECT 1", "1", ImmutableList.of("1"), true),
            new TagQuery("cases", "SELECT 2", "2", ImmutableList.of("2"), false));
  }

  private Path writeToFile(Path path, String content) {
    try {
      return Files.write(path, content.getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private <T> Stream<T> parse(YamlFileParser<T> yamlParser, Path path) {
    try {
      return yamlParser.parse(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
