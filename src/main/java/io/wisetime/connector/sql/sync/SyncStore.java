/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A store to remember the lastest synced tags at the same sync marker.
 *
 * @author shane.xie
 */
public class SyncStore {

  private ConnectorStore connectorStore;

  public SyncStore(final ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
  }

  /**
   * Persist the latest sync marker as well as the ids at that marker.
   * The TagSyncRecords provided must be sorted by sync marker in lexicographically descending order.
   */
  public void markSyncPosition(final String namespace, final LinkedList<TagSyncRecord> tagSyncRecordsInAscMarkerOrder) {
    final List<TagSyncRecord> latestSynced =
        extractMostRecentTagSyncRecordsWithSameMarker(tagSyncRecordsInAscMarkerOrder.descendingIterator());

    if (!latestSynced.isEmpty()) {
      final String latestMarker = latestSynced.get(0).getSyncMarker();
      connectorStore.putString(getMarkerKey(namespace), latestMarker);

      final String syncedIds = latestSynced.stream()
          .map(TagSyncRecord::getId)
          .collect(Collectors.joining(","));
      connectorStore.putString(getLastSyncedIdsKey(namespace), syncedIds);
    }
  }

  public String getSyncMarker(final String namespace, final String defaultSyncMarker) {
    return connectorStore.getString(getMarkerKey(namespace)).orElse(defaultSyncMarker);
  }

  public List<String> getLastSyncedIds(final String namespace) {
    return connectorStore.getString(getLastSyncedIdsKey(namespace))
        .map(refs -> refs.split(","))
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
          lastSyncMarker.isPresent() && tagSyncRecord.getSyncMarker().compareTo(lastSyncMarker.get()) <= 0,
          "TagSyncRecords must be sorted with lexicographically larger (most recent) marker first"
      );
      if (lastSyncMarker.isPresent() && lastSyncMarker.get().equals(tagSyncRecord.getSyncMarker())) {
        mostRecentSameMarker.add(tagSyncRecord);
      } else {
        break;
      }
    }
    return mostRecentSameMarker;
  }

  private String getMarkerKey(final String namespace) {
    return namespace + "_sync_marker";
  }

  private String getLastSyncedIdsKey(final String namespace) {
    return namespace + "_last_synced_ids";
  }
}