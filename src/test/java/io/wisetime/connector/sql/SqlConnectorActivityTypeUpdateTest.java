/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.queries.ActivityTypeQueryProvider;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.ActivityTypeSyncStore;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class SqlConnectorActivityTypeUpdateTest {

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ActivityTypeQueryProvider activityTypeQueryProvider = mock(ActivityTypeQueryProvider.class);
  private final ActivityTypeSyncStore activityTypeSyncStoreMock = mock(ActivityTypeSyncStore.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private SqlConnector connector;

  @BeforeEach
  void init() {
    connector = new SqlConnector(databaseMock, mock(TagQueryProvider.class), activityTypeQueryProvider);
    connector.setActivityTypeSyncStore(activityTypeSyncStoreMock);
    connector.setConnectApi(connectApiMock);
  }

  @Test
  void performActivityTypeUpdate_noQueries() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(ImmutableList.of());

    connector.performActivityTypeUpdate();

    verifyZeroInteractions(databaseMock, activityTypeSyncStoreMock, connectApiMock);
  }

  @Test
  void performActivityTypeUpdate_noActivityTypes() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(new ActivityTypeQuery("SELECT 1", ImmutableList.of())));

    final ImmutableList<ActivityTypeRecord> activityTypeRecords = ImmutableList.of();
    when(databaseMock.getActivityTypes(any()))
        .thenReturn(activityTypeRecords);
    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.FALSE);

    connector.performActivityTypeUpdate();

    // empty activity types should be synced
    verify(connectApiMock, times(1)).syncActivityTypes(activityTypeRecords);
    verify(activityTypeSyncStoreMock, times(1)).markSynced(activityTypeRecords);
  }

  @Test
  void performActivityTypeUpdate_syncedLongTimeAgo() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(new ActivityTypeQuery("SELECT 1", ImmutableList.of())));

    final ImmutableList<ActivityTypeRecord> activityTypeRecords = ImmutableList.of(
        RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(any()))
        .thenReturn(activityTypeRecords);
    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.TRUE);
    when(activityTypeSyncStoreMock.lastSyncedOlderThan(any()))
        .thenReturn(Boolean.TRUE);

    connector.performActivityTypeUpdate();

    verify(connectApiMock, times(1)).syncActivityTypes(activityTypeRecords);
    verify(activityTypeSyncStoreMock, times(1)).markSynced(activityTypeRecords);
  }

  @Test
  void performActivityTypeUpdate_alreadySyncedRecently() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(new ActivityTypeQuery("SELECT 1", ImmutableList.of())));

    final ImmutableList<ActivityTypeRecord> activityTypeRecords = ImmutableList.of(
        RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(any()))
        .thenReturn(activityTypeRecords);
    when(activityTypeSyncStoreMock.isSynced(activityTypeRecords))
        .thenReturn(Boolean.TRUE);
    when(activityTypeSyncStoreMock.lastSyncedOlderThan(any()))
        .thenReturn(Boolean.FALSE);

    connector.performActivityTypeUpdate();

    verify(connectApiMock, never()).syncActivityTypes(anyList());
    verify(activityTypeSyncStoreMock, never()).markSynced(anyList());
  }

  @Test
  void performActivityTypeUpdate_exceptionShouldNotPreventNextRun() {
    when(activityTypeQueryProvider.getQueries())
        .thenReturn(ImmutableList.of(new ActivityTypeQuery("SELECT 1", ImmutableList.of())));

    final ImmutableList<ActivityTypeRecord> activityTypeRecords = ImmutableList.of(
        RandomEntities.randomActivityTypeRecord());
    when(databaseMock.getActivityTypes(any()))
        .thenThrow(new RuntimeException("First call throws"))
        .thenReturn(activityTypeRecords);

    // first call does nothing as throws exception
    assertThrows(RuntimeException.class, () -> connector.performActivityTypeUpdate());
    verify(connectApiMock, never()).syncActivityTypes(anyList());
    verify(activityTypeSyncStoreMock, never()).markSynced(anyList());

    // second call should proceed normally
    connector.performActivityTypeUpdate();
    verify(connectApiMock, times(1)).syncActivityTypes(activityTypeRecords);
    verify(activityTypeSyncStoreMock, times(1)).markSynced(activityTypeRecords);
  }

}
