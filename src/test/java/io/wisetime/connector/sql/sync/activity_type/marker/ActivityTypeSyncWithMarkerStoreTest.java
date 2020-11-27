/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncWithMarkerStoreTest {

  private final Faker faker = Faker.instance();

  private final ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private final ActivityTypeSyncWithMarkerStore activityTypeSyncStore =
      new ActivityTypeSyncWithMarkerStore(mockConnectorStore);

  @Test
  void getSyncMarker_noMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.empty());

    assertThat(activityTypeSyncStore.getSyncMarker(query))
        .as("should return initial sync marker from the query")
        .isEqualTo(query.getInitialSyncMarker());
    verify(mockConnectorStore, times(1)).getString(syncMarkerKey(query));
  }

  @Test
  void getSyncMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final String savedSyncMarker = faker.numerify("syncMarker-###");
    when(mockConnectorStore.getString(syncMarkerKey(query)))
        .thenReturn(Optional.of(savedSyncMarker));

    assertThat(activityTypeSyncStore.getSyncMarker(query))
        .as("should return from the store")
        .isEqualTo(savedSyncMarker);
  }

  @Test
  void saveSyncMarker_empty() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    assertThatThrownBy(() -> activityTypeSyncStore.saveSyncMarker(query, List.of()))
        .as("empty list is not allowed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("can't be empty");
    verifyZeroInteractions(mockConnectorStore);
  }

  @Test
  void saveSyncMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final List<ActivityTypeRecord> activityTypes = IntStream.range(0, faker.number().numberBetween(1, 5))
        .mapToObj(idx -> RandomEntities.randomActivityTypeRecord())
        .collect(Collectors.toList());

    final String savedSyncMarker = activityTypeSyncStore.saveSyncMarker(query, activityTypes);

    final String latestSyncMarker = activityTypes.get(activityTypes.size() - 1).getSyncMarker();
    assertThat(savedSyncMarker)
        .as("saved marker should be returned")
        .isEqualTo(latestSyncMarker);
    // check that the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(syncMarkerKey(query), latestSyncMarker);
  }

  @Test
  void getRefreshMarker_noMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.empty());

    assertThat(activityTypeSyncStore.getRefreshMarker(query))
        .as("should return initial sync marker from the query")
        .isEqualTo(query.getInitialSyncMarker());
    verify(mockConnectorStore, times(1)).getString(refreshMarkerKey(query));
  }

  @Test
  void getRefreshMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final String savedSyncMarker = faker.numerify("syncMarker-###");
    when(mockConnectorStore.getString(refreshMarkerKey(query)))
        .thenReturn(Optional.of(savedSyncMarker));

    assertThat(activityTypeSyncStore.getRefreshMarker(query))
        .as("should return from the store")
        .isEqualTo(savedSyncMarker);
  }

  @Test
  void saveRefreshMarker_empty() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    assertThatThrownBy(() -> activityTypeSyncStore.saveRefreshMarker(query, List.of()))
        .as("empty list is not allowed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("can't be empty");
    verifyZeroInteractions(mockConnectorStore);
  }

  @Test
  void saveRefreshMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final List<ActivityTypeRecord> activityTypes = IntStream.range(0, faker.number().numberBetween(1, 5))
        .mapToObj(idx -> RandomEntities.randomActivityTypeRecord())
        .collect(Collectors.toList());

    final String savedRefreshMarker = activityTypeSyncStore.saveRefreshMarker(query, activityTypes);

    final String latestSyncMarker = activityTypes.get(activityTypes.size() - 1).getSyncMarker();
    assertThat(savedRefreshMarker)
        .as("saved marker should be returned")
        .isEqualTo(latestSyncMarker);
    // check that the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(refreshMarkerKey(query), latestSyncMarker);
  }

  @Test
  void clearRefreshMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.clearRefreshMarker(query);

    verify(mockConnectorStore, times(1)).putString(refreshMarkerKey(query), null);
  }

  @Test
  void saveSyncSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    final String syncSessionId = faker.numerify("sync-session-###");

    activityTypeSyncStore.saveSyncSession(query, syncSessionId);

    verify(mockConnectorStore, times(1)).putString(syncSessionKey(query), syncSessionId);
  }

  @Test
  void getSyncSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.getSyncSession(query);

    verify(mockConnectorStore, times(1)).getString(syncSessionKey(query));
  }

  @Test
  void saveRefreshSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    final String syncSessionId = faker.numerify("sync-session-###");

    activityTypeSyncStore.saveRefreshSession(query, syncSessionId);

    verify(mockConnectorStore, times(1)).putString(refreshSessionKey(query), syncSessionId);
  }

  @Test
  void getRefreshSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.getRefreshSession(query);

    verify(mockConnectorStore, times(1)).getString(refreshSessionKey(query));
  }

  @Test
  void clearRefreshSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.clearRefreshSession(query);

    verify(mockConnectorStore, times(1)).putString(refreshSessionKey(query), null);
  }

  @Test
  void checkKeys() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    assertThat(activityTypeSyncStore.syncMarkerKey(query))
        .isEqualTo(syncMarkerKey(query));
    assertThat(activityTypeSyncStore.refreshMarkerKey(query))
        .isEqualTo(refreshMarkerKey(query));
    assertThat(activityTypeSyncStore.syncSessionKey(query))
        .isEqualTo(syncSessionKey(query));
    assertThat(activityTypeSyncStore.refreshSessionKey(query))
        .isEqualTo(refreshSessionKey(query));
  }

  private String syncMarkerKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_marker";
  }

  private String refreshMarkerKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_refresh_marker";
  }

  private String syncSessionKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_session";
  }

  private String refreshSessionKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_refresh_session";
  }

}
