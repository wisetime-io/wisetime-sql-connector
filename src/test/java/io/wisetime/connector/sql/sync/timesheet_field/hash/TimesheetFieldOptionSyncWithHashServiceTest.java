/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.hash;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class TimesheetFieldOptionSyncWithHashServiceTest {

  private final Faker faker = Faker.instance();

  private final ConnectedDatabase databaseMock = mock(ConnectedDatabase.class);
  private final ConnectApi connectApiMock = mock(ConnectApi.class);
  private final TimesheetFieldOptionSyncWithHashStore syncStoreMock = mock(TimesheetFieldOptionSyncWithHashStore.class);

  private TimesheetFieldOptionSyncWithHashService syncService;

  @BeforeEach
  void init() {
    syncService = new TimesheetFieldOptionSyncWithHashService(mock(ConnectorStore.class), connectApiMock, databaseMock);
    syncService.setTimesheetFieldOptionSyncStore(syncStoreMock);
  }

  @Test
  void performTimesheetFieldOptionUpdate_noOptions() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final List<TimesheetFieldOptionRecord> options = List.of();
    final String optionsHash = faker.numerify("hash-###");
    when(databaseMock.getTimesheetFieldOptions(query)).thenReturn(options);
    when(syncStoreMock.computeHash(options)).thenReturn(optionsHash);
    when(syncStoreMock.isSynced(query.getFieldId(), optionsHash)).thenReturn(Boolean.FALSE);

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startTimesheetFieldOptionsSyncSession(query.getFieldId())).thenReturn(syncSessionId);

    // empty options should still be synced
    syncService.performTimesheetFieldOptionUpdate(query);

    assertSynced(query, options, optionsHash, syncSessionId);
  }

  @Test
  void performTimesheetFieldOptionUpdate_syncedLongTimeAgo() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final List<TimesheetFieldOptionRecord> options = List.of(RandomEntities.randomTimesheetFieldOptionRecord());
    final String optionsHash = faker.numerify("hash-###");
    when(databaseMock.getTimesheetFieldOptions(query)).thenReturn(options);
    when(syncStoreMock.computeHash(options)).thenReturn(optionsHash);
    when(syncStoreMock.isSynced(query.getFieldId(), optionsHash)).thenReturn(Boolean.TRUE);
    when(syncStoreMock.lastSyncedOlderThan(anyString(), any())).thenReturn(Boolean.TRUE);

    final String syncSessionId = faker.numerify("session-###");
    when(connectApiMock.startTimesheetFieldOptionsSyncSession(query.getFieldId())).thenReturn(syncSessionId);

    syncService.performTimesheetFieldOptionUpdate(query);

    assertSynced(query, options, optionsHash, syncSessionId);
  }

  @Test
  void performTimesheetFieldOptionUpdate_alreadySyncedRecently() {
    final TimesheetFieldOptionQuery query = RandomEntities.randomTimesheetFieldOptionQuery();

    final List<TimesheetFieldOptionRecord> options = List.of(RandomEntities.randomTimesheetFieldOptionRecord());
    final String optionsHash = faker.numerify("hash-###");
    when(databaseMock.getTimesheetFieldOptions(query)).thenReturn(options);
    when(syncStoreMock.computeHash(options)).thenReturn(optionsHash);
    when(syncStoreMock.isSynced(query.getFieldId(), optionsHash)).thenReturn(Boolean.TRUE);
    when(syncStoreMock.lastSyncedOlderThan(anyString(), any())).thenReturn(Boolean.FALSE);

    syncService.performTimesheetFieldOptionUpdate(query);

    verifyNoInteractions(connectApiMock);
    verify(syncStoreMock, never()).markSynced(anyString(), anyString());
  }

  private void assertSynced(TimesheetFieldOptionQuery query, List<TimesheetFieldOptionRecord> options,
      String optionsHash, String syncSessionId) {
    final InOrder inOrder = Mockito.inOrder(connectApiMock, syncStoreMock);
    inOrder.verify(connectApiMock, times(1)).startTimesheetFieldOptionsSyncSession(query.getFieldId());
    inOrder.verify(connectApiMock, times(1)).syncTimesheetFieldOptions(query.getFieldId(), options, syncSessionId);
    inOrder.verify(connectApiMock, times(1)).completeTimesheetFieldOptionsSyncSession(query.getFieldId(), syncSessionId);
    inOrder.verify(syncStoreMock, times(1)).markSynced(query.getFieldId(), optionsHash);
  }
}
