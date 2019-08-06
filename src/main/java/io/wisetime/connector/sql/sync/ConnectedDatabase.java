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

  private HikariDataSource dataSource;
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
      final String sql, final String syncMarker, final List<String> lastSyncedReferences) {

    Preconditions.checkArgument(
        sql.contains(":previous_sync_marker") && sql.contains(":previous_sync_references"),
        "The tag query SQL must contain both :previous_sync_marker and :previous_sync_references"
    );

    final LinkedList<TagSyncRecord> results = new LinkedList<>();
    query()
        .select(sql)
        .namedParam("previous_sync_marker", syncMarker)
        .namedParam("previous_sync_references", lastSyncedReferences)
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
    tagSyncRecord.setReference(resultSet.getString("reference"));
    tagSyncRecord.setTagName(resultSet.getString("tag_name"));
    tagSyncRecord.setAdditionalKeyword(resultSet.getString("additional_keyword"));
    tagSyncRecord.setTagDescription(resultSet.getString("tag_description"));
    tagSyncRecord.setSyncMarker(resultSet.getString("sync_marker"));
    return tagSyncRecord;
  }
}
