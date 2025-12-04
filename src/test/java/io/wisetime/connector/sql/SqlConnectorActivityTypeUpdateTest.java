/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.queries.ActivityTypeQueryProvider;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeSyncService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class SqlConnectorActivityTypeUpdateTest {

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ActivityTypeQueryProvider activityTypeQueryProvider = mock(ActivityTypeQueryProvider.class);
  private final ActivityTypeSyncService activityTypeSyncWithHashServiceMock = mock(ActivityTypeSyncService.class);
  private final ActivityTypeSyncService activityTypeSyncWithMarkerServiceMock = mock(ActivityTypeSyncService.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private SqlConnector connector;

  @BeforeEach
  void init() {
    connector = new SqlConnector(databaseMock, mock(TagQueryProvider.class), activityTypeQueryProvider);
    connector.setActivityTypeSyncWithHashService(activityTypeSyncWithHashServiceMock);
    connector.setActivityTypeSyncWithMarkerService(activityTypeSyncWithMarkerServiceMock);
    connector.setConnectApi(connectApiMock);
  }

  @Test
  void performActivityTypeUpdate_noQueries() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of());

    connector.performActivityTypeUpdate();

    verifyZeroInteractions(
        databaseMock, activityTypeSyncWithHashServiceMock, activityTypeSyncWithMarkerServiceMock, connectApiMock);
  }

  @Test
  void performActivityTypeUpdateSlowLoop_noQueries() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of());

    connector.performActivityTypeUpdateSlowLoop();

    verifyZeroInteractions(
        databaseMock, activityTypeSyncWithHashServiceMock, activityTypeSyncWithMarkerServiceMock, connectApiMock);
  }

  @Test
  void performActivityTypeUpdate_usingHash() {
    // query without sync_marker, hashing method should be used
    final ActivityTypeQuery activityTypeQuery = RandomEntities.randomActivityTypeQuery();
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of(activityTypeQuery));

    // check regular update
    connector.performActivityTypeUpdate();
    // service with hashing should be used
    verify(activityTypeSyncWithHashServiceMock, times(1)).performActivityTypeUpdate(activityTypeQuery);
    verifyNoMoreInteractions(activityTypeSyncWithHashServiceMock);
    reset(activityTypeSyncWithHashServiceMock);

    // check slow loop
    connector.performActivityTypeUpdateSlowLoop();
    // service with hashing should be used
    verify(activityTypeSyncWithHashServiceMock, times(1)).performActivityTypeUpdateSlowLoop(activityTypeQuery);
    verifyNoMoreInteractions(activityTypeSyncWithHashServiceMock);

    verifyZeroInteractions(activityTypeSyncWithMarkerServiceMock);
  }

  @Test
  void performActivityTypeUpdate_usingSyncMarker() {
    // query with sync_marker, marker method should be used
    final ActivityTypeQuery activityTypeQuery = queryWithSyncMarker();

    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of(activityTypeQuery));

    // check regular update
    connector.performActivityTypeUpdate();
    // service with marker should be used
    verify(activityTypeSyncWithMarkerServiceMock, times(1)).performActivityTypeUpdate(activityTypeQuery);
    verifyNoMoreInteractions(activityTypeSyncWithMarkerServiceMock);
    reset(activityTypeSyncWithMarkerServiceMock);

    // check slow loop
    connector.performActivityTypeUpdateSlowLoop();
    // service with marker should be used
    verify(activityTypeSyncWithMarkerServiceMock, times(1)).performActivityTypeUpdateSlowLoop(activityTypeQuery);
    verifyNoMoreInteractions(activityTypeSyncWithMarkerServiceMock);

    verifyZeroInteractions(activityTypeSyncWithHashServiceMock);
  }

  @Test
  void performActivityTypeUpdate_exceptionShouldNotPreventNextRun() {
    final ActivityTypeQuery activityTypeQuery = RandomEntities.randomActivityTypeQuery();

    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of(activityTypeQuery));

    doThrow(new RuntimeException("First call throws"))
        .doNothing()
        .when(activityTypeSyncWithHashServiceMock).performActivityTypeUpdate(activityTypeQuery);

    // first call does nothing as throws exception
    assertThrows(RuntimeException.class, () -> connector.performActivityTypeUpdate());
    verify(activityTypeSyncWithHashServiceMock, times(1)).performActivityTypeUpdate(activityTypeQuery);
    verifyZeroInteractions(connectApiMock, databaseMock, activityTypeSyncWithMarkerServiceMock);
    reset(activityTypeSyncWithHashServiceMock);

    // second call should proceed normally
    connector.performActivityTypeUpdate();
    verify(activityTypeSyncWithHashServiceMock, times(1)).performActivityTypeUpdate(activityTypeQuery);
  }

  @Test
  void performActivityTypeUpdateSlowLoop_exceptionShouldNotPreventNextRun() {
    final ActivityTypeQuery activityTypeQuery = queryWithSyncMarker();

    when(activityTypeQueryProvider.getQueries())
        .thenReturn(List.of(activityTypeQuery));

    doThrow(new RuntimeException("First call throws"))
        .doNothing()
        .when(activityTypeSyncWithMarkerServiceMock).performActivityTypeUpdateSlowLoop(activityTypeQuery);

    // first call does nothing as throws exception
    assertThrows(RuntimeException.class, () -> connector.performActivityTypeUpdateSlowLoop());
    verify(activityTypeSyncWithMarkerServiceMock, times(1)).performActivityTypeUpdateSlowLoop(activityTypeQuery);
    verifyZeroInteractions(connectApiMock, databaseMock, activityTypeSyncWithHashServiceMock);
    reset(activityTypeSyncWithMarkerServiceMock);

    // second call should proceed normally
    connector.performActivityTypeUpdateSlowLoop();
    verify(activityTypeSyncWithMarkerServiceMock, times(1)).performActivityTypeUpdateSlowLoop(activityTypeQuery);
  }

  private ActivityTypeQuery queryWithSyncMarker() {
    return new ActivityTypeQuery(
        "SELECT TOP 100"
            + "  [ACTIVITYCODE] AS [code],"
            + "  [ACTIVITYCODE] AS [sync_marker],"
            + "  [ACTIVITYNAME] AS [label]"
            + "  [ACTIVITYDESCRIPTION] AS [description]"
            + "  FROM [dbo].[TEST_ACTIVITYCODES]"
            + "  WHERE [ACTIVITYCODE] > :previous_sync_marker"
            + "  ORDER BY [sync_marker]",
        "0", List.of());
  }

}
