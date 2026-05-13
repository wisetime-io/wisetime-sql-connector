/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Utility class designed for data draining. Requires at least defining {@link this#fetchBatch} supplier for fetching data in
 * batches and {@link this#processBatch} consumer for its processing. DrainRun will fetch all the data until {@link
 * this#fetchBatch} supplier returns empty batch.
 *
 * @author yehor.lashkul
 */
@RequiredArgsConstructor
public class DrainRun<T> {

  private static final long DEFAULT_DELAY = 500;
  private static final Supplier<Boolean> ALWAYS_ALLOW = () -> true;

  private final Supplier<Boolean> allowSync;
  private final Supplier<List<T>> fetchBatch;
  private final Consumer<List<T>> processBatch;
  private final long delayInMillis;

  public DrainRun(Supplier<Boolean> allowSync, Supplier<List<T>> fetchBatch, Consumer<List<T>> processBatch) {
    this(allowSync, fetchBatch, processBatch, DEFAULT_DELAY);
  }

  public DrainRun(Supplier<List<T>> fetchBatch, Consumer<List<T>> processBatch) {
    this(ALWAYS_ALLOW, fetchBatch, processBatch);
  }

  /**
   * Fetches all the data until {@link this#fetchBatch} supplier returns empty batch. Throws exception if {@link
   * this#fetchBatch} supplier returns the same batch 2 times straight. It protects against infinite loop if it always
   * returns the same batch.
   */
  public void run() {
    List<T> prevBatch = List.of();
    List<T> newBatch;
    while (allowSync.get() && (newBatch = fetchBatch.get()).size() > 0) {
      if (newBatch.equals(prevBatch)) {
        throw new RuntimeException("Fetched the same batch twice. Looks like wrong query was used");
      }
      processBatch.accept(newBatch);
      prevBatch = newBatch;
      try {
        TimeUnit.MILLISECONDS.sleep(delayInMillis);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
