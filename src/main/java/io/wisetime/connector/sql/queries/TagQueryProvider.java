/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Reads tag queries from the provided file path.
 *
 * Watches the file for changes so that each call of TagQueryProvider#getQueries returns the latest tag queries from the
 * configuration file.
 *
 * @author shane.xie
 */
@Slf4j
public class TagQueryProvider extends QueryProvider<TagQuery> {

  public TagQueryProvider(final Path tagSqlPath, EventBus eventBus) {
    super(tagSqlPath, eventBus);
  }

  @Override
  List<TagQuery> parseSqlFile(Path path) {
    try {
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml(new Constructor(TagQuery.class));
      final ImmutableList<TagQuery> queries = StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
          .map(query -> (TagQuery) query)
          .map(TagQueryProvider::enforceValid)
          .map(TagQueryProvider::applyDefaults)
          .map(TagQueryProvider::trimSql)
          .collect(ImmutableList.toImmutableList());

      // Fail early to give the operator a tight feedback loop when configuring the connector
      Preconditions.checkArgument(TagQuery.allUnique(queries), "Tag SQL queries must be unique");
      return queries;

    } catch (IOException ioe) {
      log.error("Failed to read tag SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }

  private static TagQuery enforceValid(final TagQuery query) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getName()), "Tag SQL query name is required");
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getInitialSyncMarker()),
        "Initial sync marker for tag SQL query %s can't be empty", query.getName());
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getSql()),
        "SQL is required for tag SQL query %s", query.getName());
    Preconditions.checkArgument(!query.getSkippedIds().isEmpty(),
        "Skipped ID list is required for tag SQL query %s. Use a sentinel value if none apply.",
        query.getName());
    return query;
  }

  private static TagQuery trimSql(final TagQuery query) {
    query.setSql(query.getSql().trim());
    return query;
  }

  private static TagQuery applyDefaults(final TagQuery query) {
    if (query.getContinuousResync() == null) {
      query.setContinuousResync(true);
    }
    return query;
  }
}
