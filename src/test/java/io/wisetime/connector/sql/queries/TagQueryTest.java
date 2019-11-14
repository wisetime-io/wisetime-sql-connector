/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import java.util.Objects;

import static io.wisetime.connector.sql.RandomEntities.randomTagQuery;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author shane.xie
 */
class TagQueryTest {

  /**
   * SyncStore uses hashCode() as part of the key under which it stores sync state for TagQueries.
   *
   * The hashing implementation shouldn't be changed without careful consideration because it will
   * cause tag updates to run all over again from the initial sync marker when connectors are updated.
   */
  @Test
  public void testHashCode() {
    final TagQuery query = randomTagQuery("cases");
    assertThat(query.hashCode())
        .as("The hash code should be derived from the query's SQL, initial sync marker and skikpped IDs")
        .isEqualTo(Objects.hash(query.getSql(), query.getInitialSyncMarker(), query.getSkippedIds()));
  }

  @Test
  public void testEquals() {
    final TagQuery query1 = new TagQuery();
    query1.setName("cases");
    query1.setSql("SELECT");
    query1.setSkippedIds(ImmutableList.of());
    query1.setInitialSyncMarker("");

    final TagQuery query2 = new TagQuery();
    query2.setName("projects");
    query2.setSql("SELECT");
    query2.setSkippedIds(ImmutableList.of());
    query2.setInitialSyncMarker("");

    assertThat(query1)
        .as("Tag query name is not used in equals comparison")
        .isEqualTo(query2);
  }

  @Test void testAllUnique() {
    final TagQuery query1 = randomTagQuery("cases");
    final TagQuery query2 = randomTagQuery("projects");

    assertThat(TagQuery.allUnique(ImmutableList.of(query1, query2))).isTrue();
    assertThat(TagQuery.allUnique(ImmutableList.of(query1, query2, query1))).isFalse();
  }
}