/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.marker;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class TimesheetFieldOptionSyncWithMarkerServiceTest {

  private final Faker faker = Faker.instance();

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private final TimesheetFieldOptionSyncWithMarkerStore drainSyncStore = mock(TimesheetFieldOptionSyncWithMarkerStore.class);
  private final TimesheetFieldOptionSyncWithMarkerStore refreshSyncStore =
      mock(TimesheetFieldOptionSyncWithMarkerStore.class);

  private TimesheetFieldOptionSyncWithMarkerService syncService;

  @BeforeEach
  void init() {
    syncService = new TimesheetFieldOptionSyncWithMarkerService(mock(ConnectorStore.class), connectApiMock, databaseMock);
    syncService.setTimesheetFieldOptionDrainSyncStore(drainSyncStore);
    syncService.setTimesheetFieldOptionRefreshSyncStore(refreshSyncStore);
  }

  @Test
  void performTimesheetFieldOptionUpdate_firstRun_noOptions() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    // defines the first run
    when(drainSyncStore.getSyncMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startTimesheetFieldOptionsSyncSession(query.getFieldId()))
        .thenReturn(syncSessionId);

    when(databaseMock.getTimesheetFieldOptions(query, query.getInitialSyncMarker(), List.of()))
        .thenReturn(List.of());

    // empty options should be synced
    syncService.performTimesheetFieldOptionUpdate(query);

    // in case of the first run session should be created and completed
    verify(connectApiMock, times(1)).startTimesheetFieldOptionsSyncSession(query.getFieldId());
    verify(connectApiMock, times(1)).completeTimesheetFieldOptionsSyncSession(query.getFieldId(), syncSessionId);
    // empty options are not synced
    verify(connectApiMock, never()).syncTimesheetFieldOptions(any(), any(), any());
    verify(drainSyncStore, never()).markSyncPosition(any(), any());
  }

  @Test
  void performTimesheetFieldOptionUpdate_firstRun() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final TimesheetFieldOptionRecord firstBatchOption = RandomEntities.randomTimesheetFieldOptionRecord("111");
    final TimesheetFieldOptionRecord secondBatchOption = RandomEntities.randomTimesheetFieldOptionRecord("222");

    when(drainSyncStore.getSyncMarker(query))
        // defines the first run
        .thenReturn(query.getInitialSyncMarker())
        // invoked again when requesting option records
        .thenReturn(query.getInitialSyncMarker())
        // invoked after the first batch
        .thenReturn(firstBatchOption.getSyncMarker())
        // invoked after the second batch
        .thenReturn(secondBatchOption.getSyncMarker());

    when(drainSyncStore.getLastSyncedCodes(query))
        .thenReturn(List.of())
        .thenReturn(List.of(firstBatchOption.getCode()))
        .thenReturn(List.of(secondBatchOption.getCode()));

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startTimesheetFieldOptionsSyncSession(query.getFieldId()))
        .thenReturn(syncSessionId);

    when(databaseMock.getTimesheetFieldOptions(query, query.getInitialSyncMarker(), List.of()))
        .thenReturn(List.of(firstBatchOption));
    when(databaseMock.getTimesheetFieldOptions(query, firstBatchOption.getSyncMarker(),
        List.of(firstBatchOption.getCode())))
        .thenReturn(List.of(secondBatchOption));
    when(databaseMock.getTimesheetFieldOptions(query, secondBatchOption.getSyncMarker(),
        List.of(secondBatchOption.getCode())))
        .thenReturn(List.of());

    syncService.performTimesheetFieldOptionUpdate(query);

    // check that all options (2 batches) were synced within session, sync marker saved after each batch
    final InOrder inOrder = Mockito.inOrder(connectApiMock, drainSyncStore);
    inOrder.verify(connectApiMock, times(1)).startTimesheetFieldOptionsSyncSession(query.getFieldId());
    inOrder.verify(connectApiMock, times(1))
        .syncTimesheetFieldOptions(eq(query.getFieldId()), eq(List.of(firstBatchOption)), eq(syncSessionId), any());
    inOrder.verify(drainSyncStore, times(1)).markSyncPosition(query, List.of(firstBatchOption));
    inOrder.verify(connectApiMock, times(1))
        .syncTimesheetFieldOptions(eq(query.getFieldId()), eq(List.of(secondBatchOption)), eq(syncSessionId), any());
    inOrder.verify(drainSyncStore, times(1)).markSyncPosition(query, List.of(secondBatchOption));
    inOrder.verify(connectApiMock, times(1)).completeTimesheetFieldOptionsSyncSession(eq(query.getFieldId()), any());
  }

  @Test
  void performTimesheetFieldOptionUpdate_subsequentRuns_noOptions() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();
    final String prevSyncMarker = faker.numerify("sync_marker_###");
    final List<String> prevSyncedCodes = List.of(faker.numerify("code-###"), faker.numerify("code-###"));

    when(drainSyncStore.getSyncMarker(query))
        .thenReturn(prevSyncMarker);
    when(drainSyncStore.getLastSyncedCodes(query))
        .thenReturn(prevSyncedCodes);

    when(databaseMock.getTimesheetFieldOptions(query, prevSyncMarker, prevSyncedCodes))
        .thenReturn(List.of());

    syncService.performTimesheetFieldOptionUpdate(query);

    // empty options should NOT be synced as NO session should be started/completed
    verifyNoInteractions(connectApiMock);
    verify(drainSyncStore, never()).markSyncPosition(any(), any());
  }

  @Test
  @DisplayName("performTimesheetFieldOptionUpdate should clear sync position in case of invalid session")
  void performTimesheetFieldOptionUpdate_onInvalidSession() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    // defines the first run so sync with new session will happen
    when(drainSyncStore.getSyncMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    when(databaseMock.getTimesheetFieldOptions(query, query.getInitialSyncMarker(), List.of()))
        .thenReturn(List.of(RandomEntities.randomTimesheetFieldOptionRecord()));

    // simulate session not found
    doAnswer(invocation -> {
      // invoke callback
      invocation.getArgument(3, Runnable.class).run();
      throw new RuntimeException("not found");
    }).when(connectApiMock).syncTimesheetFieldOptions(any(), any(), any(), any());

    assertThatThrownBy(() -> syncService.performTimesheetFieldOptionUpdate(query))
        .as("exception should be thrown to retry")
        .isInstanceOf(RuntimeException.class);

    // check that sync position is cleared when onInvalidSession is invoked
    // so drain run will start from the beginning
    verify(drainSyncStore, times(1)).resetSyncPosition(query);
  }

  @Test
  void performTimesheetFieldOptionUpdateSlowLoop_start_notFinished() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    // slow loop is not started yet
    when(refreshSyncStore.getSyncSession(query))
        .thenReturn(Optional.empty());

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startTimesheetFieldOptionsSyncSession(query.getFieldId()))
        .thenReturn(syncSessionId);

    when(refreshSyncStore.getSyncMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    final List<TimesheetFieldOptionRecord> batch = List.of(
        RandomEntities.randomTimesheetFieldOptionRecord(),
        RandomEntities.randomTimesheetFieldOptionRecord());
    when(databaseMock.getTimesheetFieldOptions(query, query.getInitialSyncMarker(), List.of()))
        .thenReturn(batch);

    syncService.performTimesheetFieldOptionUpdateSlowLoop(query);

    verify(connectApiMock, times(1))
        .syncTimesheetFieldOptions(eq(query.getFieldId()), eq(batch), eq(syncSessionId), any());
    verify(refreshSyncStore, times(1)).markSyncPosition(query, batch);
    // slow loop is not finished, session should not be completed
    verify(connectApiMock, never()).completeTimesheetFieldOptionsSyncSession(any(), any());
    verify(connectApiMock, never()).completeTimesheetFieldOptionsSyncSession(any(), any(), any());
  }

  @Test
  void performTimesheetFieldOptionUpdateSlowLoop_continue_finished() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    // slow loop is already started
    final String syncSessionId = faker.numerify("session-###");
    when(refreshSyncStore.getSyncSession(query))
        .thenReturn(Optional.of(syncSessionId));

    final String refreshSyncMarker = faker.numerify("sync_marker_###");
    when(refreshSyncStore.getSyncMarker(query))
        .thenReturn(refreshSyncMarker);

    final List<String> prevRefreshedCodes = List.of(faker.numerify("code-###"), faker.numerify("code-###"));
    when(refreshSyncStore.getLastSyncedCodes(query))
        .thenReturn(prevRefreshedCodes);

    // no more options, slow loop is finished
    when(databaseMock.getTimesheetFieldOptions(query, refreshSyncMarker, prevRefreshedCodes))
        .thenReturn(List.of());

    syncService.performTimesheetFieldOptionUpdateSlowLoop(query);

    // slow loop is finished, sync session should be completed
    verify(connectApiMock, times(1)).completeTimesheetFieldOptionsSyncSession(eq(query.getFieldId()), eq(syncSessionId),
        any());
    // syncMarker and syncSession should be cleared for slow loop
    verify(refreshSyncStore, times(1)).resetSyncPosition(query);

    // nothing to sync
    verify(connectApiMock, never()).syncTimesheetFieldOptions(any(), any(), any());
    // we should use previously saved session
    verify(connectApiMock, never()).startTimesheetFieldOptionsSyncSession(any());
  }

  @Test
  @DisplayName("performTimesheetFieldOptionUpdateSlowLoop should clear sync position in case of invalid session")
  void performTimesheetFieldOptionUpdateSlowLoop_onInvalidSession() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final String refreshSyncMarker = faker.numerify("sync_marker_###");
    when(refreshSyncStore.getSyncMarker(query))
        .thenReturn(refreshSyncMarker);

    final List<String> prevRefreshedCodes = List.of(faker.numerify("code-###"), faker.numerify("code-###"));
    when(refreshSyncStore.getLastSyncedCodes(query))
        .thenReturn(prevRefreshedCodes);

    // no more options, slow loop is finished
    when(databaseMock.getTimesheetFieldOptions(query, refreshSyncMarker, prevRefreshedCodes))
        .thenReturn(List.of());

    // simulate session not found
    doAnswer(invocation -> {
      // invoke callback
      invocation.getArgument(2, Runnable.class).run();
      throw new RuntimeException("not found");
    }).when(connectApiMock).completeTimesheetFieldOptionsSyncSession(any(), any(), any());

    assertThatThrownBy(() -> syncService.performTimesheetFieldOptionUpdateSlowLoop(query))
        .as("exception should be thrown to retry")
        .isInstanceOf(RuntimeException.class);

    // check that sync position is cleared when onInvalidSession is invoked
    // so slow loop will start from the beginning
    verify(refreshSyncStore, times(1)).resetSyncPosition(query);
  }
}
