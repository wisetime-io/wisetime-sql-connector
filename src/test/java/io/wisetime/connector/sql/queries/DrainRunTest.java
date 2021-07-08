/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.github.javafaker.Faker;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author yehor.lashkul
 */
class DrainRunTest {

  private final Faker faker = Faker.instance();

  @Mock
  private Supplier<Boolean> allowSyncMock;
  @Mock
  private Supplier<List<String>> fetchBatchMock;
  @Mock
  private Consumer<List<String>> processBatchMock;

  private DrainRun<String> drainRun;

  @BeforeEach
  void init() {
    MockitoAnnotations.initMocks(this);
    drainRun = new DrainRun<>(allowSyncMock, fetchBatchMock, processBatchMock, 1);
  }

  @Test
  void run_stop_dontRun() {
    when(allowSyncMock.get())
        .thenReturn(false);
    when(fetchBatchMock.get())
        .then(invocation -> List.of(faker.gameOfThrones().character()));

    drainRun.run();

    // check that batch wasn't fetched nor processed
    verifyZeroInteractions(fetchBatchMock);
    verifyZeroInteractions(processBatchMock);
  }

  @Test
  void run_stop() {
    when(allowSyncMock.get())
        .thenReturn(true, true, false);
    when(fetchBatchMock.get())
        .then(invocation -> List.of(faker.gameOfThrones().character()));

    drainRun.run();

    // check that batch was fetched and processed 2 times until stopped
    verify(fetchBatchMock, times(2)).get();
    verify(processBatchMock, times(2)).accept(any());
  }

  @Test
  void run_sameBatch() {
    when(allowSyncMock.get())
        .thenReturn(true);
    when(fetchBatchMock.get())
        .thenReturn(List.of(faker.gameOfThrones().character()));

    assertThatThrownBy(() -> drainRun.run())
        .hasMessageContainingAll("same batch", "wrong query");

    // check that batch was fetched 2 times and processed only once
    // as should fail before processing the same batch second time
    verify(fetchBatchMock, times(2)).get();
    verify(processBatchMock, times(1)).accept(any());
  }

}
