/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariDataSource;
import io.vavr.control.Try;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.codejargon.fluentjdbc.api.FluentJdbc;
import org.codejargon.fluentjdbc.api.FluentJdbcBuilder;
import org.codejargon.fluentjdbc.api.mapper.Mappers;
import org.codejargon.fluentjdbc.api.query.Query;

/**
 * The SQL database that the connector integrates with.
 *
 * @author shane.xie
 */
public class ConnectedDatabase {

  private final HikariDataSource dataSource;
  private final FluentJdbc fluentJdbc;

  public ConnectedDatabase(final HikariDataSource dataSource) {
    this.dataSource = dataSource;
    this.fluentJdbc = new FluentJdbcBuilder().connectionProvider(dataSource).build();
  }

  public boolean isAvailable() {
    return Try.of(() -> query().select("SELECT 1").firstResult(Mappers.singleInteger())).isSuccess();
  }

  public LinkedList<TagSyncRecord> getTagsToSync(
      final String sql, final String syncMarker, final List<String> skippedIds) {

    Preconditions.checkArgument(!skippedIds.isEmpty(), "skippedIds must not be empty");
    Preconditions.checkArgument(
        sql.contains(":previous_sync_marker") && sql.contains(":skipped_ids"),
        "The tag query SQL must contain both :previous_sync_marker and :skipped_ids"
    );

    final LinkedList<TagSyncRecord> results = new LinkedList<>();
    query()
        .select(sql)
        .namedParam("previous_sync_marker", syncMarker)
        .namedParam("skipped_ids", skippedIds)
        .iterateResult(TagSyncRecord.fluentJdbcMapper(), results::add);
    return results;
  }

  public List<ActivityTypeRecord> getActivityTypes(final ActivityTypeQuery query) {
    return getActivityTypes(query, StringUtils.EMPTY);
  }

  public List<ActivityTypeRecord> getActivityTypes(final ActivityTypeQuery query, final String syncMarker) {
    query.enforceValid();
    return query()
        .select(query.getSql())
        .namedParam("skipped_codes", query.getSkippedCodes())
        .namedParam("previous_sync_marker", syncMarker)
        .listResult(ActivityTypeRecord.fluentJdbcMapper(query.hasSyncMarker()));
  }

  public void close() {
    dataSource.close();
  }

  @VisibleForTesting
  Query query() {
    return fluentJdbc.query();
  }
}
