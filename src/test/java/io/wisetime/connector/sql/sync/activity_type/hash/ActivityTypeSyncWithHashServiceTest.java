/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type.hash;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * @author yehor.lashkul
 */
class ActivityTypeSyncWithHashServiceTest {

  private final Faker faker = Faker.instance();

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private final ActivityTypeSyncStore activityTypeSyncStoreMock = mock(ActivityTypeSyncStore.class);

  private ActivityTypeSyncWithHashService activityTypeSyncWithHashService;

  @BeforeEach
  void init() {
    activityTypeSyncWithHashService =
        new ActivityTypeSyncWithHashService(mock(ConnectorStore.class), connectApiMock, databaseMock);
    activityTypeSyncWithHashService.setActivityTypeSyncStore(activityTypeSyncStoreMock);
  }

  @Test
  void performActivityTypeUpdate_noActivityTypes() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final List<ActivityTypeRecord> activityTypeRecords = List.of();
    when(databaseMock.getActivityTypes(query))
        .thenReturn(activityTypeRecords);

    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.FALSE);

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startSyncSession())
        .thenReturn(syncSessionId);

    // empty activity types should be synced
    activityTypeSyncWithHashService.performActivityTypeUpdate(query);

    assertSynced(activityTypeRecords, syncSessionId);

  }

  @Test
  void performActivityTypeUpdate_syncedLongTimeAgo() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final List<ActivityTypeRecord> activityTypeRecords = List.of(RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(query))
        .thenReturn(activityTypeRecords);

    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.TRUE);
    when(activityTypeSyncStoreMock.lastSyncedOlderThan(any()))
        .thenReturn(Boolean.TRUE);

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startSyncSession())
        .thenReturn(syncSessionId);

    activityTypeSyncWithHashService.performActivityTypeUpdate(query);

    assertSynced(activityTypeRecords, syncSessionId);
  }

  @Test
  void performActivityTypeUpdate_alreadySyncedRecently() {
    final ActivityTypeQuery query = RandomEntities.randomActivityTypeQuery();

    final List<ActivityTypeRecord> activityTypeRecords = List.of(
        RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(query))
        .thenReturn(activityTypeRecords);

    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.TRUE);
    when(activityTypeSyncStoreMock.lastSyncedOlderThan(any()))
        .thenReturn(Boolean.FALSE);

    activityTypeSyncWithHashService.performActivityTypeUpdate(query);

    verifyZeroInteractions(connectApiMock);
    verify(activityTypeSyncStoreMock, never()).markSynced(anyList());
  }

  @Test
  void performActivityTypeUpdateSlowLoop() {
    activityTypeSyncWithHashService.performActivityTypeUpdateSlowLoop(RandomEntities.randomActivityTypeQuery());

    // no slow loop so no interactions
    verifyZeroInteractions(connectApiMock, activityTypeSyncStoreMock, databaseMock);
  }

  private void assertSynced(List<ActivityTypeRecord> activityTypeRecords, String syncSessionId) {
    final InOrder inOrder = Mockito.inOrder(connectApiMock, activityTypeSyncStoreMock);
    inOrder.verify(connectApiMock, times(1)).startSyncSession();
    inOrder.verify(connectApiMock, times(1)).syncActivityTypes(activityTypeRecords, syncSessionId);
    inOrder.verify(connectApiMock, times(1)).completeSyncSession(syncSessionId);
    inOrder.verify(activityTypeSyncStoreMock, times(1)).markSynced(activityTypeRecords);
  }

}
