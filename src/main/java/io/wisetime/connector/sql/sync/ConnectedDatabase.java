/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
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
    try {
      query().select("SELECT 1").firstResult(Mappers.singleInteger());
      return true;
    } catch (Exception ex) {
      return false;
    }
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
        .iterateResult(resultSet -> results.add(toTagSyncRecord(resultSet)));
    return results;
  }

  public void close() {
    dataSource.close();
  }

  @VisibleForTesting
  Query query() {
    return fluentJdbc.query();
  }

  private TagSyncRecord toTagSyncRecord(final ResultSet resultSet) throws SQLException {
    final TagSyncRecord tagSyncRecord = new TagSyncRecord();
    tagSyncRecord.setId(resultSet.getString("id"));
    tagSyncRecord.setTagName(resultSet.getString("tag_name"));
    tagSyncRecord.setAdditionalKeyword(resultSet.getString("additional_keyword"));
    tagSyncRecord.setTagDescription(resultSet.getString("tag_description"));
    tagSyncRecord.setSyncMarker(resultSet.getString("sync_marker"));
    return tagSyncRecord;
  }
}
