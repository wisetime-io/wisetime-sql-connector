/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.wisetime.connector.sql.queries.ActivityTypeQueryProvider;
import io.wisetime.connector.sql.queries.QueryProvider.Listener;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionSyncService;
import io.wisetime.generated.connect.TimesheetField;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SqlConnectorTimesheetFieldOptionUpdateTest {

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final TimesheetFieldOptionQueryProvider timesheetFieldOptionQueryProvider =
      mock(TimesheetFieldOptionQueryProvider.class);
  private final TimesheetFieldOptionSyncService timesheetFieldOptionSyncWithHashServiceMock =
      mock(TimesheetFieldOptionSyncService.class);
  private final TimesheetFieldOptionSyncService timesheetFieldOptionSyncWithMarkerServiceMock =
      mock(TimesheetFieldOptionSyncService.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private SqlConnector connector;
  private Listener<TimesheetFieldOptionQuery> queriesUpdatedListener;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void init() {
    connector = new SqlConnector(
        databaseMock, mock(TagQueryProvider.class), mock(ActivityTypeQueryProvider.class),
        timesheetFieldOptionQueryProvider);
    connector.setTimesheetFieldOptionSyncWithHashService(timesheetFieldOptionSyncWithHashServiceMock);
    connector.setTimesheetFieldOptionSyncWithMarkerService(timesheetFieldOptionSyncWithMarkerServiceMock);
    connector.setConnectApi(connectApiMock);

    final ArgumentCaptor<Listener<TimesheetFieldOptionQuery>> listenerCaptor = ArgumentCaptor.forClass(Listener.class);
    verify(timesheetFieldOptionQueryProvider).setListener(listenerCaptor.capture());
    queriesUpdatedListener = listenerCaptor.getValue();
  }

  @Test
  void performTimesheetFieldOptionUpdate_noQueries() {
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of());

    connector.performTimesheetFieldOptionUpdate();

    verifyNoInteractions(
        databaseMock, timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock,
        connectApiMock);
  }

  @Test
  void performTimesheetFieldOptionUpdateSlowLoop_noQueries() {
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of());

    connector.performTimesheetFieldOptionUpdateSlowLoop();

    verifyNoInteractions(
        databaseMock, timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock,
        connectApiMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_usingHash() {
    // query without sync_marker, hashing method should be used
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField(query.getFieldId())));

    // check regular update
    connector.performTimesheetFieldOptionUpdate();
    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(query);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithHashServiceMock);
    reset(timesheetFieldOptionSyncWithHashServiceMock);

    // check slow loop
    connector.performTimesheetFieldOptionUpdateSlowLoop();
    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdateSlowLoop(query);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithHashServiceMock);

    verifyNoInteractions(timesheetFieldOptionSyncWithMarkerServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_usingSyncMarker() {
    // query with sync_marker, marker method should be used
    final TimesheetFieldOptionQuery query = queryWithSyncMarker("field-1");

    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField(query.getFieldId())));

    // check regular update
    connector.performTimesheetFieldOptionUpdate();
    verify(timesheetFieldOptionSyncWithMarkerServiceMock, times(1)).performTimesheetFieldOptionUpdate(query);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithMarkerServiceMock);
    reset(timesheetFieldOptionSyncWithMarkerServiceMock);

    // check slow loop
    connector.performTimesheetFieldOptionUpdateSlowLoop();
    verify(timesheetFieldOptionSyncWithMarkerServiceMock, times(1)).performTimesheetFieldOptionUpdateSlowLoop(query);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithMarkerServiceMock);

    verifyNoInteractions(timesheetFieldOptionSyncWithHashServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_multipleFieldsRoutedIndependently() {
    // unlike activity types, multiple timesheet fields can be configured at once, each independently
    // routed to the hash or marker service based on its own query
    final TimesheetFieldOptionQuery hashQuery = RandomEntities.randomTimesheetFieldOptionQuery("field-hash");
    final TimesheetFieldOptionQuery markerQuery = queryWithSyncMarker("field-marker");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(hashQuery, markerQuery));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField("field-hash"), connectedField("field-marker")));

    connector.performTimesheetFieldOptionUpdate();

    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(hashQuery);
    verify(timesheetFieldOptionSyncWithMarkerServiceMock, times(1)).performTimesheetFieldOptionUpdate(markerQuery);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_exceptionShouldNotPreventNextRun() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField(query.getFieldId())));

    doThrow(new RuntimeException("First call throws"))
        .doNothing()
        .when(timesheetFieldOptionSyncWithHashServiceMock).performTimesheetFieldOptionUpdate(query);

    // first call does nothing as throws exception
    assertThrows(RuntimeException.class, () -> connector.performTimesheetFieldOptionUpdate());
    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(query);
    verifyNoInteractions(databaseMock, timesheetFieldOptionSyncWithMarkerServiceMock);
    reset(timesheetFieldOptionSyncWithHashServiceMock);

    // second call should proceed normally
    connector.performTimesheetFieldOptionUpdate();
    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(query);
  }

  @Test
  void performTimesheetFieldOptionUpdateSlowLoop_exceptionShouldNotPreventNextRun() {
    final TimesheetFieldOptionQuery query = queryWithSyncMarker("field-1");

    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField(query.getFieldId())));

    doThrow(new RuntimeException("First call throws"))
        .doNothing()
        .when(timesheetFieldOptionSyncWithMarkerServiceMock).performTimesheetFieldOptionUpdateSlowLoop(query);

    // first call does nothing as throws exception
    assertThrows(RuntimeException.class, () -> connector.performTimesheetFieldOptionUpdateSlowLoop());
    verify(timesheetFieldOptionSyncWithMarkerServiceMock, times(1)).performTimesheetFieldOptionUpdateSlowLoop(query);
    verifyNoInteractions(databaseMock, timesheetFieldOptionSyncWithHashServiceMock);
    reset(timesheetFieldOptionSyncWithMarkerServiceMock);

    // second call should proceed normally
    connector.performTimesheetFieldOptionUpdateSlowLoop();
    verify(timesheetFieldOptionSyncWithMarkerServiceMock, times(1)).performTimesheetFieldOptionUpdateSlowLoop(query);
  }

  @Test
  void performTimesheetFieldOptionUpdate_unknownFieldId_isSkipped() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery("field-unknown");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    // WiseTime does not have "field-unknown" connected
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField("field-other")));

    connector.performTimesheetFieldOptionUpdate();

    verifyNoInteractions(timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdateSlowLoop_unknownFieldId_isSkipped() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery("field-unknown");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    // WiseTime does not have "field-unknown" connected
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField("field-other")));

    connector.performTimesheetFieldOptionUpdateSlowLoop();

    verifyNoInteractions(timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_onlyKnownFieldsAreSynced() {
    // one configured field is connected, the other is not (e.g. disconnected in WiseTime, or a typo)
    final TimesheetFieldOptionQuery knownQuery = RandomEntities.randomTimesheetFieldOptionQuery("field-known");
    final TimesheetFieldOptionQuery unknownQuery = RandomEntities.randomTimesheetFieldOptionQuery("field-unknown");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(knownQuery, unknownQuery));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField("field-known")));

    connector.performTimesheetFieldOptionUpdate();

    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(knownQuery);
    verifyNoMoreInteractions(timesheetFieldOptionSyncWithHashServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_customFieldId_isSkipped() {
    // WiseTime also lists custom fields created by users, but its option sync endpoints reject them
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery("field-custom");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(query));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(customField("field-custom")));

    connector.performTimesheetFieldOptionUpdate();
    connector.performTimesheetFieldOptionUpdateSlowLoop();

    verifyNoInteractions(timesheetFieldOptionSyncWithHashServiceMock, timesheetFieldOptionSyncWithMarkerServiceMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_failingFieldDoesNotPreventOtherFieldsSync() {
    final TimesheetFieldOptionQuery failingQuery = RandomEntities.randomTimesheetFieldOptionQuery("field-failing");
    final TimesheetFieldOptionQuery healthyQuery = RandomEntities.randomTimesheetFieldOptionQuery("field-healthy");
    when(timesheetFieldOptionQueryProvider.getQueries())
        .thenReturn(List.of(failingQuery, healthyQuery));
    when(connectApiMock.listTimesheetFields())
        .thenReturn(List.of(connectedField("field-failing"), connectedField("field-healthy")));
    doThrow(new RuntimeException("Invalid SQL"))
        .when(timesheetFieldOptionSyncWithHashServiceMock).performTimesheetFieldOptionUpdate(failingQuery);
    doThrow(new RuntimeException("Invalid SQL"))
        .when(timesheetFieldOptionSyncWithHashServiceMock).performTimesheetFieldOptionUpdateSlowLoop(failingQuery);

    // the run may still report the failure, as long as every other field has been synced
    catchThrowable(() -> connector.performTimesheetFieldOptionUpdate());
    catchThrowable(() -> connector.performTimesheetFieldOptionUpdateSlowLoop());

    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdate(healthyQuery);
    verify(timesheetFieldOptionSyncWithHashServiceMock, times(1)).performTimesheetFieldOptionUpdateSlowLoop(healthyQuery);
  }

  @Test
  void queriesUpdatedListener_doesNotPropagateSyncFailure() {
    // the listener is called from the file watch loop, which stops watching the file if the listener throws
    when(connectApiMock.listTimesheetFields())
        .thenThrow(new RuntimeException("WiseTime API unavailable"));

    assertThatCode(() -> queriesUpdatedListener
        .onQueriesUpdated(List.of(RandomEntities.randomTimesheetFieldOptionQuery())))
        .doesNotThrowAnyException();
  }

  private TimesheetFieldOptionQuery queryWithSyncMarker(String fieldId) {
    return new TimesheetFieldOptionQuery(
        fieldId,
        "SELECT [CODE] AS [code], [CODE] AS [sync_marker] FROM [dbo].[MATTER_TYPE]"
            + " WHERE [CODE] > :previous_sync_marker ORDER BY [sync_marker]",
        "0", Collections.emptyList());
  }

  private TimesheetField connectedField(String fieldId) {
    return new TimesheetField().id(fieldId).label(fieldId).connected(true);
  }

  private TimesheetField customField(String fieldId) {
    return new TimesheetField().id(fieldId).label(fieldId).connected(false);
  }
}
