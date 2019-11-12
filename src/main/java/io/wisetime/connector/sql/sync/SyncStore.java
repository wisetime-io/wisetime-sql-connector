/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.queries.TagQuery;
import lombok.extern.slf4j.Slf4j;

import static io.wisetime.connector.sql.format.LogFormatter.ellipsize;

/**
 * A store to remember the latest synced tags at the same sync marker.
 *
 * Can be configured with a key space. SyncStores with different configured key spaces effectively
 * behave like separate stores even if they share the same underlying ConnectorStore. They store
 * their state in their own separate key spaces.
 *
 * @author shane.xie
 */
@Slf4j
public class SyncStore {

  private static final String DELIMITER = "@@@";
  private final ConnectorStore connectorStore;
  private final String keySpace;

  /**
   * Create a SyncStore with default key space.
   *
   * @param connectorStore
   */
  public SyncStore(final ConnectorStore connectorStore) {
    // Do not change the default key space value. Doing so will cause existing stores that have
    // the default configuration to lose state.
    this(connectorStore, "");
  }

  /**
   * Create a SyncStore, providing a custom key space.
   *
   * @param connectorStore
   * @param keySpace
   */
  public SyncStore(final ConnectorStore connectorStore, final String keySpace) {
    this.connectorStore = connectorStore;
    this.keySpace = keySpace;
  }

  /**
   * Persist the latest sync marker as well as the ids at that marker.
   * The TagSyncRecords provided must be sorted by sync marker in ascending order.
   */
  public void markSyncPosition(final TagQuery tagQuery, final LinkedList<TagSyncRecord> tagSyncRecordsInAscMarkerOrder) {
    final List<TagSyncRecord> currentSyncBatch =
        extractMostRecentTagSyncRecordsWithSameMarker(tagSyncRecordsInAscMarkerOrder.descendingIterator());

    if (!currentSyncBatch.isEmpty()) {
      final String previousMarker = getSyncMarker(tagQuery);
      final String latestMarker = currentSyncBatch.get(0).getSyncMarker();
      connectorStore.putString(markerKey(tagQuery), latestMarker);

      final Stream<String> previousSyncedIds = getLastSyncedIds(tagQuery).stream();
      final Stream<String> latestSyncedIds = currentSyncBatch.stream().map(TagSyncRecord::getId);
      List<String> syncedIdsAtSameMarker;

      if (latestMarker.equals(previousMarker)) {
        syncedIdsAtSameMarker = Stream.concat(previousSyncedIds, latestSyncedIds).collect(Collectors.toList());
      } else {
        syncedIdsAtSameMarker = latestSyncedIds.collect(Collectors.toList());
      }
      connectorStore.putString(lastSyncedIdsKey(tagQuery), StringUtils.join(syncedIdsAtSameMarker, DELIMITER));

      log.info("Last synced IDs at same marker ({}): {}", syncedIdsAtSameMarker.size(),
          ellipsize(syncedIdsAtSameMarker));
    }
  }

  public String getSyncMarker(final TagQuery tagQuery) {
    return connectorStore.getString(markerKey(tagQuery)).orElse(tagQuery.getInitialSyncMarker());
  }

  public List<String> getLastSyncedIds(final TagQuery tagQuery) {
    return connectorStore.getString(lastSyncedIdsKey(tagQuery))
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
      if (lastSyncMarker.get().equals(tagSyncRecord.getSyncMarker())) {
        mostRecentSameMarker.add(tagSyncRecord);
      } else {
        break;
      }
    }
    return mostRecentSameMarker;
  }

  private String markerKey(final TagQuery tagQuery) {
    return keySpace + tagQuery.hashCode() + "_sync_marker";
  }

  private String lastSyncedIdsKey(final TagQuery tagQuery) {
    return keySpace + tagQuery.hashCode() + "_last_synced_ids";
  }
}
