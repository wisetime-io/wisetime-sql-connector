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
  void getLastSyncedCodes_empty() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    when(mockConnectorStore.getString(lastSyncedCodesKey(query)))
        .thenReturn(Optional.empty());

    assertThat(activityTypeSyncStore.getLastSyncedCodes(query))
        .as("should return empty list")
        .isEqualTo(List.of());
  }

  @Test
  void getLastSyncedCodes() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    final String code1 = faker.numerify("code-###");
    final String code2 = faker.numerify("code-###");

    final String savedLatestCodes = code1 + "@@@" + code2;
    when(mockConnectorStore.getString(lastSyncedCodesKey(query)))
        .thenReturn(Optional.of(savedLatestCodes));

    assertThat(activityTypeSyncStore.getLastSyncedCodes(query))
        .as("should return from the store parsed by delimiter")
        .isEqualTo(List.of(code1, code2));
  }

  @Test
  void markSyncPosition_empty() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    assertThatThrownBy(() -> activityTypeSyncStore.markSyncPosition(query, List.of()))
        .as("empty list is not allowed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("can't be empty");
    verifyZeroInteractions(mockConnectorStore);
  }

  @Test
  void markSyncPosition() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final ActivityTypeRecord activityTypeRecord1 = RandomEntities.randomActivityTypeRecord().toBuilder()
        .syncMarker("111")
        .build();
    final ActivityTypeRecord activityTypeRecord2 = RandomEntities.randomActivityTypeRecord().toBuilder()
        .syncMarker("222")
        .build();
    final ActivityTypeRecord activityTypeRecord3 = RandomEntities.randomActivityTypeRecord().toBuilder()
        .syncMarker("222")
        .build();

    final List<ActivityTypeRecord> activityTypes = List.of(activityTypeRecord1, activityTypeRecord2, activityTypeRecord3);

    activityTypeSyncStore.markSyncPosition(query, activityTypes);

    // check that the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(syncMarkerKey(query), "222");
    // check that the codes of activity types with the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(lastSyncedCodesKey(query),
        activityTypeRecord2.getCode() + "@@@" + activityTypeRecord3.getCode());
  }

  @Test
  void clearSyncMarker() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.clearSyncMarker(query);

    verify(mockConnectorStore, times(1)).putString(syncMarkerKey(query), null);
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
  void clearSyncSession() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    activityTypeSyncStore.clearSyncSession(query);

    verify(mockConnectorStore, times(1)).putString(syncSessionKey(query), null);
  }

  @Test
  void checkKeys() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    assertThat(activityTypeSyncStore.syncMarkerKey(query))
        .isEqualTo(syncMarkerKey(query));
    assertThat(activityTypeSyncStore.syncSessionKey(query))
        .isEqualTo(syncSessionKey(query));
    assertThat(activityTypeSyncStore.lastSyncedCodesKey(query))
        .isEqualTo(lastSyncedCodesKey(query));
  }

  private String syncMarkerKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_marker";
  }

  private String syncSessionKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_sync_session";
  }

  private String lastSyncedCodesKey(final ActivityTypeQuery query) {
    return query.hashCode() + "_activity_type_last_sync_codes";
  }
}
