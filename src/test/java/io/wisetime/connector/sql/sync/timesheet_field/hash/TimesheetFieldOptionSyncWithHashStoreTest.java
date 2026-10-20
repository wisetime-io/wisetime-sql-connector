/*
 * Copyright (c) 2026 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.timesheet_field.hash;

import static io.wisetime.connector.sql.RandomEntities.randomTimesheetFieldOptionRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import com.google.common.collect.ImmutableList;
import io.wisetime.connector.datastore.ConnectorStore;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TimesheetFieldOptionSyncWithHashStoreTest {

  private final Faker faker = Faker.instance();

  private final ConnectorStore mockConnectorStore = mock(ConnectorStore.class);
  private final TimesheetFieldOptionSyncWithHashStore syncStore =
      new TimesheetFieldOptionSyncWithHashStore(mockConnectorStore);

  @Test
  void isSynced_notSyncedYet() {
    final String fieldId = faker.numerify("field-###");
    when(mockConnectorStore.getString(syncStore.hashKey(fieldId)))
        .thenReturn(Optional.empty());

    assertThat(syncStore.isSynced(fieldId, "some hash"))
        .as("there was no sync yet")
        .isFalse();
  }

  @Test
  void isSynced() {
    final String fieldId = faker.numerify("field-###");

    when(mockConnectorStore.getString(syncStore.hashKey(fieldId)))
        .thenReturn(Optional.of("same hash"));
    assertThat(syncStore.isSynced(fieldId, "same hash"))
        .as("same hash -> synced")
        .isTrue();

    when(mockConnectorStore.getString(syncStore.hashKey(fieldId)))
        .thenReturn(Optional.of("another hash"));
    assertThat(syncStore.isSynced(fieldId, "same hash"))
        .as("another hash -> not synced")
        .isFalse();
  }

  @Test
  void isSynced_differentFieldsAreIndependent() {
    final String fieldOne = "field-1";
    final String fieldTwo = "field-2";

    when(mockConnectorStore.getString(syncStore.hashKey(fieldOne)))
        .thenReturn(Optional.of("hash"));
    when(mockConnectorStore.getString(syncStore.hashKey(fieldTwo)))
        .thenReturn(Optional.empty());

    assertThat(syncStore.isSynced(fieldOne, "hash")).isTrue();
    assertThat(syncStore.isSynced(fieldTwo, "hash")).isFalse();
  }

  @Test
  void computeHash() {
    syncStore.setHashFunction(records -> "computed hash");

    assertThat(syncStore.computeHash(randomOptions())).isEqualTo("computed hash");
  }

  @Test
  void lastSyncedOlderThan_notSyncedYet() {
    final String fieldId = faker.numerify("field-###");
    when(mockConnectorStore.getLong(syncStore.lastSyncKey(fieldId)))
        .thenReturn(Optional.empty());

    assertThat(syncStore.lastSyncedOlderThan(fieldId, Duration.ofDays(100)))
        .as("there was no sync yet")
        .isTrue();
  }

  @Test
  void lastSyncedOlderThan() {
    final String fieldId = faker.numerify("field-###");
    when(mockConnectorStore.getLong(syncStore.lastSyncKey(fieldId)))
        .thenReturn(Optional.of(Instant.now()
            .minus(1, ChronoUnit.DAYS)
            .minus(1, ChronoUnit.SECONDS)
            .toEpochMilli()));

    assertThat(syncStore.lastSyncedOlderThan(fieldId, Duration.ofDays(1)))
        .isTrue();
    assertThat(syncStore.lastSyncedOlderThan(fieldId, Duration.ofDays(2)))
        .isFalse();
  }

  @Test
  void markSynced() {
    final String fieldId = faker.numerify("field-###");

    syncStore.markSynced(fieldId, "hash");

    verify(mockConnectorStore, times(1)).putString(syncStore.hashKey(fieldId), "hash");
    final ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(mockConnectorStore, times(1)).putLong(eq(syncStore.lastSyncKey(fieldId)), captor.capture());
    assertThat(Instant.ofEpochMilli(captor.getValue()))
        .isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
  }

  private List<TimesheetFieldOptionRecord> randomOptions() {
    return ImmutableList.of(randomTimesheetFieldOptionRecord());
  }
}
