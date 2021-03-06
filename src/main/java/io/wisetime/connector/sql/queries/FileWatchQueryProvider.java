/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * @author shane.xie
 * @author yehor.lashkul
 */
@Slf4j
abstract class FileWatchQueryProvider<T> implements QueryProvider<T> {

  private final ExecutorService fileWatchExecutor;
  private final CompletableFuture<Void> fileWatch;
  private final AtomicReference<List<T>> queries;
  private final AtomicReference<Listener<T>> listener = new AtomicReference<>(Listener.noOp());

  public FileWatchQueryProvider(final Path sqlPath) {
    queries = new AtomicReference<>(parseSqlFile(sqlPath));
    fileWatchExecutor = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("file-watch-" + getClass().getSimpleName())
            .build());
    fileWatch = startWatchingFile(sqlPath);
  }

  @Override
  public List<T> getQueries() {
    return queries.get();
  }

  @Override
  public void setListener(Listener<T> listener) {
    this.listener.set(listener);
  }

  @Override
  public void stop() {
    fileWatch.cancel(true);
    fileWatchExecutor.shutdownNow();
  }

  @Override
  public boolean isHealthy() {
    return !fileWatch.isDone() && CollectionUtils.isNotEmpty(queries.get());
  }

  // Blocking, only meant for use in tests
  @VisibleForTesting
  List<T> waitForQueryChange(final List<T> awaitedResult, Duration timeout) throws InterruptedException {
    long stopTime = System.currentTimeMillis() + timeout.toMillis();
    while (!queries.get().equals(awaitedResult)) {
      if (System.currentTimeMillis() > stopTime) {
        throw new RuntimeException("Timeout");
      }
      Thread.sleep(50);
    }
    return queries.get();
  }

  private CompletableFuture<Void> startWatchingFile(final Path path) {
    return CompletableFuture.supplyAsync(() -> {
      try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {

        path.getParent().register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);

        while (true) {
          final WatchKey key = watchService.take();

          for (WatchEvent<?> event : key.pollEvents()) {
            if (path.endsWith((Path) event.context())) {
              switch (event.kind().name()) {
                case "ENTRY_CREATE":
                case "ENTRY_MODIFY":
                  queries.set(parseSqlFile(path));
                  listener.get().onQueriesUpdated(queries.get());
                  break;

                case "ENTRY_DELETE":
                  queries.set(ImmutableList.of());
                  listener.get().onQueriesUpdated(queries.get());
                  break;

                default:
                  log.warn("Unexpected file watch event: {}", event.kind());
              }
            }
          }
          key.reset();
        }

      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Autoload failed for SQL configuration file", e);
      }
    }, fileWatchExecutor);
  }

  abstract List<T> parseSqlFile(Path path);
}
