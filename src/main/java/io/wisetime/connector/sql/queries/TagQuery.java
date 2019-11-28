/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
  private Boolean continuousResync;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TagQuery query = (TagQuery) o;
    return Objects.equals(sql, query.sql) &&
        Objects.equals(initialSyncMarker, query.initialSyncMarker) &&
        Objects.equals(skippedIds, query.skippedIds) &&
        Objects.equals(continuousResync, query.continuousResync);
  }

  /**
   * This hashing logic was originally implemented in SyncStore. SyncStore uses the hash code as
   * part of the key under which sync state is stored for each query. It is important that the
   * original implementation is kept.
   */
  @Override
  public int hashCode() {
    if (continuousResync) {
      // Continuous resync was not in initial implementation, defaults to true for backwards compatibility
      return Objects.hash(sql, initialSyncMarker, skippedIds);
    }
    return Objects.hash(sql, initialSyncMarker, skippedIds, continuousResync);
  }

  public static boolean allUnique(Collection<TagQuery> queries) {
    return queries.size() == queries.stream().distinct().count();
  }
}
