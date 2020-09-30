/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

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
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml(new Constructor(ActivityTypeQuery.class));
      final ImmutableList<ActivityTypeQuery> queries = StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
          .map(query -> (ActivityTypeQuery) query)
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
