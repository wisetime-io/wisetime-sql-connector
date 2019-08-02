/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import java.util.ArrayList;
import java.util.Arrays;
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
   * Persist the latest sync marker as well as the references at that marker.
   * The TagSyncRecords provided must be sorted by sync marker in lexicographically descending order.
   */
  public void markSyncPosition(final String namespace, final Iterable<TagSyncRecord> tagSyncRecordsInDescMarkerOrder) {
    final List<TagSyncRecord> latestSynced =
        extractMostRecentTagSyncRecordsWithSameMarker(tagSyncRecordsInDescMarkerOrder);

    if (!latestSynced.isEmpty()) {
      final String latestMarker = latestSynced.get(0).getSyncMarker();
      connectorStore.putString(getMarkerKey(namespace), latestMarker);

      final String syncedReferences = latestSynced.stream()
          .map(TagSyncRecord::getReference)
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

  private List<TagSyncRecord> extractMostRecentTagSyncRecordsWithSameMarker(
      final Iterable<TagSyncRecord> sortedTagSyncRecords) {

    Optional<String> lastSyncMarker = Optional.empty();
    List<TagSyncRecord> mostRecentSameMarker = new ArrayList<>();

    for (TagSyncRecord tagSyncRecord : sortedTagSyncRecords) {
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

  private String getReferencesKey(final String namespace) {
    return namespace + "_last_synced_references";
  }
}