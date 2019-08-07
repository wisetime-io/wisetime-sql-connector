/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A store to remember the latest synced tags at the same sync marker.
 *
 * @author shane.xie
 */
public class SyncStore {

  private static final String DELIMITER = "@@@";

  private ConnectorStore connectorStore;

  public SyncStore(final ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
  }

  /**
   * Persist the latest sync marker as well as the ids at that marker.
   * The TagSyncRecords provided must be sorted by sync marker in lexicographically ascending order.
   */
  public void markSyncPosition(final TagQuery tagQuery, final LinkedList<TagSyncRecord> tagSyncRecordsInAscMarkerOrder) {
    final List<TagSyncRecord> latestSynced =
        extractMostRecentTagSyncRecordsWithSameMarker(tagSyncRecordsInAscMarkerOrder.descendingIterator());

    if (!latestSynced.isEmpty()) {
      final String latestMarker = latestSynced.get(0).getSyncMarker();
      connectorStore.putString(getMarkerKey(tagQuery), latestMarker);

      final String syncedIds = latestSynced.stream()
          .map(TagSyncRecord::getId)
          .collect(Collectors.joining(DELIMITER));
      connectorStore.putString(getLastSyncedIdsKey(tagQuery), syncedIds);
    }
  }

  public String getSyncMarker(final TagQuery tagQuery) {
    return connectorStore.getString(getMarkerKey(tagQuery)).orElse(tagQuery.getInitialSyncMarker());
  }

  public List<String> getLastSyncedIds(final TagQuery tagQuery) {
    return connectorStore.getString(getLastSyncedIdsKey(tagQuery))
        .map(refs -> refs.split(DELIMITER))
        .map(Arrays::asList)
        .orElse(ImmutableList.of());
  }

  private List<TagSyncRecord> extractMostRecentTagSyncRecordsWithSameMarker(
      final Iterator<TagSyncRecord> tagSyncRecordsDescending) {

    Optional<String> lastSyncMarker = Optional.empty();
    List<TagSyncRecord> mostRecentSameMarker = new ArrayList<>();

    while (tagSyncRecordsDescending.hasNext()) {
      final TagSyncRecord tagSyncRecord = tagSyncRecordsDescending.next();
      if (!lastSyncMarker.isPresent()) {
        lastSyncMarker = Optional.of(tagSyncRecord.getSyncMarker());
      }
      Preconditions.checkArgument(
          tagSyncRecord.getSyncMarker().compareTo(lastSyncMarker.get()) <= 0,
          "TagSyncRecords must be sorted with lexicographically larger (most recent) marker first"
      );
      if (lastSyncMarker.get().equals(tagSyncRecord.getSyncMarker())) {
        mostRecentSameMarker.add(tagSyncRecord);
      } else {
        break;
      }
    }
    return mostRecentSameMarker;
  }

  private String getMarkerKey(final TagQuery tagQuery) {
    return tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_sync_marker";
  }

  private String getLastSyncedIdsKey(final TagQuery tagQuery) {
    return tagQuery.getName() + "_" + tagQuery.getSql().hashCode() + "_last_synced_ids";
  }
}
