/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A store to remember the lastest synced items at the same sync marker.
 *
 * @author shane.xie
 */
class SyncStore {

  private ConnectorStore connectorStore;

  SyncStore(final ConnectorStore connectorStore) {
    this.connectorStore = connectorStore;
  }

  /**
   * Persist the latest sync marker as well as the sync item references at that marker.
   * The sync items provided must be sorted by sync marker in lexicographically descending order.
   */
  public void markSyncPosition(final String namespace, final Iterable<SyncItem> syncItemsInDescMarkerOrder,
      final Function<SyncItem, String> referenceField) {
    final List<SyncItem> latestSynced = extractMostRecentItemsWithSameMarker(syncItemsInDescMarkerOrder);

    if (!latestSynced.isEmpty()) {
      final String latestMarker = latestSynced.get(0).getSyncMarker();
      connectorStore.putString(getMarkerKey(namespace), latestMarker);

      final String syncedReferences = latestSynced.stream()
          .map(referenceField)
          .collect(Collectors.joining(","));
      connectorStore.putString(getReferencesKey(namespace), syncedReferences);
    }
  }

  public Optional<String> getSyncMarker(final String namespace) {
    return connectorStore.getString(getMarkerKey(namespace));
  }

  public List<String> getLastSyncedReferences(final String namespace) {
    return connectorStore.getString(getReferencesKey(namespace))
        .map(refs -> refs.split(","))
        .map(Arrays::asList)
        .orElse(ImmutableList.of());
  }

  private List<SyncItem> extractMostRecentItemsWithSameMarker(final Iterable<SyncItem> sortedSyncItems) {
    Optional<String> lastSyncMarker = Optional.empty();
    List<SyncItem> mostRecentSameMarker = new ArrayList<>();

    for (SyncItem item : sortedSyncItems) {
      if (!lastSyncMarker.isPresent()) {
        lastSyncMarker = Optional.of(item.getSyncMarker());
      }
      Preconditions.checkArgument(
          lastSyncMarker.isPresent() && item.getSyncMarker().compareTo(lastSyncMarker.get()) <= 0,
          "SyncItems list must be sorted with lexicographically larger (most recent) marker first"
      );
      if (lastSyncMarker.isPresent() && lastSyncMarker.get().equals(item.getSyncMarker())) {
        mostRecentSameMarker.add(item);
      } else {
        break;
      }
    }
    return mostRecentSameMarker;
  }

  private String getMarkerKey(final String namespace) {
    return namespace + "_sync_marker";
  }

  private String getReferencesKey(final String namespace) {
    return namespace + "_last_synced_references";
  }
}