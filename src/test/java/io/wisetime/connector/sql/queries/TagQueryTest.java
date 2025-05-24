/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static io.wisetime.connector.sql.RandomEntities.randomTagQuery;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

/**
 * @author shane.xie
 */
class TagQueryTest {

  @Test
  public void testEquals() {
    final TagQuery query1 = new TagQuery();
    query1.setName("cases");
    query1.setSql("SELECT");
    query1.setSkippedIds(ImmutableList.of());
    query1.setInitialSyncMarker("");
    query1.setContinuousResync(true);

    final TagQuery query2 = new TagQuery();
    query2.setName("projects");
    query2.setSql("SELECT");
    query2.setSkippedIds(ImmutableList.of());
    query2.setInitialSyncMarker("");
    query2.setContinuousResync(true);

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

  @Test void testHashCode_continuous_resync_true_default_and_not_used_in_hash_for_backwards_compatiblity() {
    final TagQuery query = randomTagQuery("cases");
    query.setContinuousResync(true);
    assertThat(query.hashCode())
        .as("The hash code should be derived from the query's SQL, initial sync marker and skikpped IDs")
        .isEqualTo(Objects.hash(query.getSql(), query.getInitialSyncMarker(), query.getSkippedIds()));
  }

  @Test void testHashCode_continuous_resync_false_used_in_hash() {
    final TagQuery query = randomTagQuery("cases");
    query.setContinuousResync(false);
    assertThat(query.hashCode())
        .as("The hash code should be derived from the query's SQL, initial sync marker and skikpped IDs")
        .isEqualTo(Objects.hash(
            query.getSql(), query.getInitialSyncMarker(), query.getSkippedIds(), query.getContinuousResync()
        ));
  }
}