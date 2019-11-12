/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import org.junit.jupiter.api.Test;

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
}