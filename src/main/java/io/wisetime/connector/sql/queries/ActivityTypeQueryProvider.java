/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * @author yehor.lashkul
 */
@Slf4j
public class ActivityTypeQueryProvider extends FileWatchQueryProvider<ActivityTypeQuery> {

  public ActivityTypeQueryProvider(Path sqlPath) {
    super(sqlPath);
  }

  @Override
  List<ActivityTypeQuery> parseSqlFile(Path path) {
    try {
      final ImmutableList<ActivityTypeQuery> queries = new YamlFileParser<>(ActivityTypeQuery.class)
          .parse(path)
          .peek(ActivityTypeQuery::enforceValid)
          .map(this::trimSql)
          .collect(ImmutableList.toImmutableList());

      // Fail early to give the operator a tight feedback loop when configuring the connector
      Preconditions.checkArgument(queries.size() <= 1, "At most one activity type SQL query must be provided");
      return queries;
    } catch (IOException ioe) {
      log.error("Failed to read activity type SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }

  private ActivityTypeQuery trimSql(final ActivityTypeQuery query) {
    query.setSql(query.getSql().trim());
    return query;
  }

}
