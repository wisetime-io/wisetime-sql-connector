/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author shane.xie
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagQuery {

  private String name;
  private String sql;
  private String initialSyncMarker;
  private List<String> skippedIds;

  /**
   * This hashing logic was originally implemented in SyncStore. SyncStore uses the hash code as
   * part of the key under which sync state is stored for each query. It is important that the
   * original implementation is kept.
   */
  @Override
  public int hashCode() {
    return Objects.hash(sql, initialSyncMarker, skippedIds);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TagQuery query = (TagQuery) o;
    return com.google.common.base.Objects.equal(sql, query.sql) &&
        com.google.common.base.Objects.equal(initialSyncMarker, query.initialSyncMarker) &&
        com.google.common.base.Objects.equal(skippedIds, query.skippedIds);
  }

  public static boolean allUnique(Collection<TagQuery> queries) {
    final List<TagQuery> distinctQueries = queries.stream().distinct().collect(Collectors.toList());
    return queries.size() == distinctQueries.size();
  }
}
