/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads timesheet field option queries from the provided file path, one YAML document per connected field.
 *
 * Watches the file for changes so that each call of TimesheetFieldOptionQueryProvider#getQueries returns the latest
 * queries from the configuration file.
 */
@Slf4j
public class TimesheetFieldOptionQueryProvider extends FileWatchQueryProvider<TimesheetFieldOptionQuery> {

  public TimesheetFieldOptionQueryProvider(Path sqlPath) {
    super(sqlPath);
  }

  @Override
  List<TimesheetFieldOptionQuery> parseSqlFile(Path path) {
    try {
      final ImmutableList<TimesheetFieldOptionQuery> queries = new YamlFileParser<>(TimesheetFieldOptionQuery.class)
          .parse(path)
          .peek(TimesheetFieldOptionQuery::enforceValid)
          .map(this::trimSql)
          .collect(ImmutableList.toImmutableList());

      // Fail early to give the operator a tight feedback loop when configuring the connector
      Preconditions.checkArgument(TimesheetFieldOptionQuery.allUniqueFieldIds(queries),
          "Timesheet field option SQL queries must have unique field IDs");
      return queries;
    } catch (IOException ioe) {
      log.error("Failed to read timesheet field option SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }

  private TimesheetFieldOptionQuery trimSql(final TimesheetFieldOptionQuery query) {
    query.setSql(query.getSql().trim());
    return query;
  }
}
