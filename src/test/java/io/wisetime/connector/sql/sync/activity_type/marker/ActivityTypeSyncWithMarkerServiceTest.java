/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.marker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.RandomEntities;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncWithMarkerServiceTest {

  private final Faker faker = Faker.instance();

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private final ActivityTypeSyncWithMarkerStore activityTypeSyncStoreMock = mock(ActivityTypeSyncWithMarkerStore.class);

  private ActivityTypeSyncWithMarkerService activityTypeSyncWithMarkerService;

  @BeforeEach
  void init() {
    activityTypeSyncWithMarkerService =
        new ActivityTypeSyncWithMarkerService(mock(ConnectorStore.class), connectApiMock, databaseMock);
    activityTypeSyncWithMarkerService.setActivityTypeSyncStore(activityTypeSyncStoreMock);
  }

  @Test
  void performActivityTypeUpdate_firstRun_noActivityTypes() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    // defines the first run
    when(activityTypeSyncStoreMock.getSyncMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startSyncSession())
        .thenReturn(syncSessionId);

    when(databaseMock.getActivityTypes(query, query.getInitialSyncMarker()))
        .thenReturn(List.of());

    // empty activity types should be synced
    activityTypeSyncWithMarkerService.performActivityTypeUpdate(query);

    // in case of the first run session should be created and completed
    verify(connectApiMock, times(1)).startSyncSession();
    verify(connectApiMock, times(1)).completeSyncSession(syncSessionId);
    // empty activity types are not synced
    verify(connectApiMock, never()).syncActivityTypes(any(), any());
    verify(activityTypeSyncStoreMock, never()).saveSyncMarker(any(), any());
  }

  @Test
  void performActivityTypeUpdate_firstRun() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final ActivityTypeRecord firstBatchActivityType = RandomEntities.randomActivityTypeRecord();
    final ActivityTypeRecord secondBatchActivityType = RandomEntities.randomActivityTypeRecord();

    // defines the first run
    when(activityTypeSyncStoreMock.getSyncMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startSyncSession())
        .thenReturn(syncSessionId);

    when(activityTypeSyncStoreMock.saveSyncMarker(query, List.of(firstBatchActivityType)))
        .thenReturn(firstBatchActivityType.getSyncMarker());
    when(activityTypeSyncStoreMock.saveSyncMarker(query, List.of(secondBatchActivityType)))
        .thenReturn(secondBatchActivityType.getSyncMarker());

    when(databaseMock.getActivityTypes(query, query.getInitialSyncMarker()))
        .thenReturn(List.of(firstBatchActivityType));
    when(databaseMock.getActivityTypes(query, firstBatchActivityType.getSyncMarker()))
        .thenReturn(List.of(secondBatchActivityType));
    when(databaseMock.getActivityTypes(query, secondBatchActivityType.getSyncMarker()))
        .thenReturn(List.of());

    activityTypeSyncWithMarkerService.performActivityTypeUpdate(query);

    // check that all activity types (2 batches) were synced withing session
    // sync marker should be save after each batch sync
    final InOrder inOrder = Mockito.inOrder(connectApiMock, activityTypeSyncStoreMock);
    inOrder.verify(connectApiMock, times(1)).startSyncSession();
    inOrder.verify(connectApiMock, times(1)).syncActivityTypes(List.of(firstBatchActivityType), syncSessionId);
    inOrder.verify(activityTypeSyncStoreMock, times(1)).saveSyncMarker(query, List.of(firstBatchActivityType));
    inOrder.verify(connectApiMock, times(1)).syncActivityTypes(List.of(secondBatchActivityType), syncSessionId);
    inOrder.verify(activityTypeSyncStoreMock, times(1)).saveSyncMarker(query, List.of(secondBatchActivityType));
    inOrder.verify(connectApiMock, times(1)).completeSyncSession(any());
  }

  @Test
  void performActivityTypeUpdate_subsequentRuns_noActivityTypes() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    final String prevSyncMarker = faker.numerify("sync_marker_###");

    when(activityTypeSyncStoreMock.getSyncMarker(query))
        .thenReturn(prevSyncMarker);

    when(databaseMock.getActivityTypes(query, prevSyncMarker))
        .thenReturn(List.of());

    activityTypeSyncWithMarkerService.performActivityTypeUpdate(query);

    // empty activity types should NOT be synced as NO session should be started/completed
    verifyZeroInteractions(connectApiMock);
    verify(activityTypeSyncStoreMock, never()).saveSyncMarker(any(), any());
  }

  @Test
  void performActivityTypeUpdate_subsequentRuns() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();
    final String prevSyncMarker = faker.numerify("sync_marker_###");

    final ActivityTypeRecord firstBatchActivityType = RandomEntities.randomActivityTypeRecord();
    final ActivityTypeRecord secondBatchActivityType = RandomEntities.randomActivityTypeRecord();

    when(activityTypeSyncStoreMock.getSyncMarker(query))
        .thenReturn(prevSyncMarker);

    when(activityTypeSyncStoreMock.saveSyncMarker(query, List.of(firstBatchActivityType)))
        .thenReturn(firstBatchActivityType.getSyncMarker());
    when(activityTypeSyncStoreMock.saveSyncMarker(query, List.of(secondBatchActivityType)))
        .thenReturn(secondBatchActivityType.getSyncMarker());

    when(databaseMock.getActivityTypes(query, prevSyncMarker))
        .thenReturn(List.of(firstBatchActivityType));
    when(databaseMock.getActivityTypes(query, firstBatchActivityType.getSyncMarker()))
        .thenReturn(List.of(secondBatchActivityType));
    when(databaseMock.getActivityTypes(query, secondBatchActivityType.getSyncMarker()))
        .thenReturn(List.of());

    activityTypeSyncWithMarkerService.performActivityTypeUpdate(query);

    // check that all activity types (2 batches) were synced without session
    // sync marker should be save after each batch sync
    final InOrder inOrder = Mockito.inOrder(connectApiMock, activityTypeSyncStoreMock);
    inOrder.verify(connectApiMock, times(1)).syncActivityTypes(List.of(firstBatchActivityType), "");
    inOrder.verify(activityTypeSyncStoreMock, times(1)).saveSyncMarker(query, List.of(firstBatchActivityType));
    inOrder.verify(connectApiMock, times(1)).syncActivityTypes(List.of(secondBatchActivityType), "");
    inOrder.verify(activityTypeSyncStoreMock, times(1)).saveSyncMarker(query, List.of(secondBatchActivityType));
    verify(connectApiMock, never()).startSyncSession();
    verify(connectApiMock, never()).completeSyncSession(any());
  }

  @Test
  void performActivityTypeUpdateSlowLoop_start_notFinished() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    // slow loop is not started yet
    when(activityTypeSyncStoreMock.getRefreshSession(query))
        .thenReturn(Optional.empty());

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startSyncSession())
        .thenReturn(syncSessionId);

    when(activityTypeSyncStoreMock.getRefreshMarker(query))
        .thenReturn(query.getInitialSyncMarker());

    final List<ActivityTypeRecord> batch = List.of(
        RandomEntities.randomActivityTypeRecord(),
        RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(query, query.getInitialSyncMarker()))
        .thenReturn(batch);

    activityTypeSyncWithMarkerService.performActivityTypeUpdateSlowLoop(query);

    verify(connectApiMock, times(1)).syncActivityTypes(batch, syncSessionId);
    verify(activityTypeSyncStoreMock, times(1)).saveRefreshMarker(query, batch);
    // slow loop is not finished, session should not be completed
    verify(connectApiMock, never()).completeSyncSession(any());
  }

  @Test
  void performActivityTypeUpdateSlowLoop_continue_finished() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    // slow loop is already started
    final String syncSessionId = faker.numerify("session-###");
    when(activityTypeSyncStoreMock.getRefreshSession(query))
        .thenReturn(Optional.of(syncSessionId));

    final String refreshSyncMarker = faker.numerify("sync_marker_###");
    when(activityTypeSyncStoreMock.getRefreshMarker(query))
        .thenReturn(refreshSyncMarker);

    // no more activity types, slow loop is finished
    when(databaseMock.getActivityTypes(query, refreshSyncMarker))
        .thenReturn(List.of());

    activityTypeSyncWithMarkerService.performActivityTypeUpdateSlowLoop(query);

    // slow loop is finished, sync session should be completed
    verify(connectApiMock, times(1)).completeSyncSession(syncSessionId);
    // syncMarker and syncSession should be cleared for slow loop
    verify(activityTypeSyncStoreMock, times(1)).clearRefreshMarker(query);
    verify(activityTypeSyncStoreMock, times(1)).clearRefreshSession(query);

    // nothing to sync
    verify(connectApiMock, never()).syncActivityTypes(any(), any());
    // we should use previously saved session
    verify(connectApiMock, never()).startSyncSession();
  }

}
