/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TimesheetFieldOptionSyncWithMarkerStoreTest {

  private final Faker faker = Faker.instance();

  private final ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private final TimesheetFieldOptionSyncWithMarkerStore syncStore =
      new TimesheetFieldOptionSyncWithMarkerStore(mockConnectorStore);

  @Test
  void getSyncMarker_noMarker() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.empty());

    assertThat(syncStore.getSyncMarker(query))
        .as("should return initial sync marker from the query")
        .isEqualTo(query.getInitialSyncMarker());

    when(mockConnectorStore.getString(anyString()))
        .thenReturn(Optional.of(""));

    assertThat(syncStore.getSyncMarker(query))
        .as("should return initial sync marker from the query")
        .isEqualTo(query.getInitialSyncMarker());
  }

  @Test
  void getSyncMarker() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final String savedSyncMarker = faker.numerify("syncMarker-###");
    when(mockConnectorStore.getString(syncStore.syncMarkerKey(query)))
        .thenReturn(Optional.of(savedSyncMarker));

    assertThat(syncStore.getSyncMarker(query))
        .as("should return from the store")
        .isEqualTo(savedSyncMarker);
  }

  @Test
  void getSyncMarker_differentFieldsAreIndependent() {
    final TimesheetFieldOptionQuery queryOne = RandomEntities.randomTimesheetFieldOptionQuery("field-1");
    final TimesheetFieldOptionQuery queryTwo = RandomEntities.randomTimesheetFieldOptionQuery("field-2");

    assertThat(syncStore.syncMarkerKey(queryOne)).isNotEqualTo(syncStore.syncMarkerKey(queryTwo));
  }

  @Test
  void getLastSyncedCodes_empty() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    when(mockConnectorStore.getString(syncStore.lastSyncedCodesKey(query)))
        .thenReturn(Optional.empty());

    assertThat(syncStore.getLastSyncedCodes(query))
        .as("should return empty list")
        .isEqualTo(List.of());

    when(mockConnectorStore.getString(syncStore.lastSyncedCodesKey(query)))
        .thenReturn(Optional.of(""));

    assertThat(syncStore.getLastSyncedCodes(query))
        .as("should return empty list")
        .isEqualTo(List.of());
  }

  @Test
  void getLastSyncedCodes() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();
    final String code1 = faker.numerify("code-###");
    final String code2 = faker.numerify("code-###");

    final String savedLatestCodes = code1 + "@@@" + code2;
    when(mockConnectorStore.getString(syncStore.lastSyncedCodesKey(query)))
        .thenReturn(Optional.of(savedLatestCodes));

    assertThat(syncStore.getLastSyncedCodes(query))
        .as("should return from the store parsed by delimiter")
        .isEqualTo(List.of(code1, code2));
  }

  @Test
  void resetSyncPosition() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    syncStore.resetSyncPosition(query);

    verify(mockConnectorStore, times(1)).putString(syncStore.syncMarkerKey(query), query.getInitialSyncMarker());
    verify(mockConnectorStore, times(1)).putString(syncStore.lastSyncedCodesKey(query), "");
    verify(mockConnectorStore, times(1)).putString(syncStore.syncSessionKey(query), "");
  }

  @Test
  void markSyncPosition_empty() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    assertThatThrownBy(() -> syncStore.markSyncPosition(query, List.of()))
        .as("empty list is not allowed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("can't be empty");
    verifyNoInteractions(mockConnectorStore);
  }

  @Test
  void markSyncPosition() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final TimesheetFieldOptionRecord option1 = RandomEntities.randomTimesheetFieldOptionRecord("111");
    final TimesheetFieldOptionRecord option2 = RandomEntities.randomTimesheetFieldOptionRecord("222");
    final TimesheetFieldOptionRecord option3 = RandomEntities.randomTimesheetFieldOptionRecord("222");

    final List<TimesheetFieldOptionRecord> options = List.of(option1, option2, option3);

    syncStore.markSyncPosition(query, options);

    // check that the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(syncStore.syncMarkerKey(query), "222");
    // check that the codes of options with the latest marker was saved with proper key
    verify(mockConnectorStore, times(1)).putString(syncStore.lastSyncedCodesKey(query),
        option2.getCode() + "@@@" + option3.getCode());
  }

  @Test
  void saveSyncSession() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();
    final String syncSessionId = faker.numerify("sync-session-###");

    syncStore.saveSyncSession(query, syncSessionId);

    verify(mockConnectorStore, times(1)).putString(syncStore.syncSessionKey(query), syncSessionId);
  }

  @Test
  void getSyncSession() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    when(mockConnectorStore.getString(syncStore.syncSessionKey(query)))
        .thenReturn(Optional.empty());
    assertThat(syncStore.getSyncSession(query))
        .isEmpty();

    when(mockConnectorStore.getString(syncStore.syncSessionKey(query)))
        .thenReturn(Optional.of(""));
    assertThat(syncStore.getSyncSession(query))
        .isEmpty();

    final String syncSession = faker.numerify("sync-session-###");
    when(mockConnectorStore.getString(syncStore.syncSessionKey(query)))
        .thenReturn(Optional.of(syncSession));
    assertThat(syncStore.getSyncSession(query))
        .contains(syncSession);
  }
}
