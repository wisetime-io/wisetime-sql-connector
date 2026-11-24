/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Reads tag queries from the provided file path.
 *
 * Watches the file for changes so that each call of TagQueryProvider#getQueries returns the latest tag queries from the
 * configuration file.
 *
 * @author shane.xie
 */
@Slf4j
public class TagQueryProvider extends FileWatchQueryProvider<TagQuery> {

  public TagQueryProvider(final Path tagSqlPath) {
    super(tagSqlPath);
  }

  @Override
  List<TagQuery> parseSqlFile(Path path) {
    try {
      final ImmutableList<TagQuery> queries = new YamlFileParser<>(TagQuery.class)
          .parse(path)
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
