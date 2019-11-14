/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads tag queries from the provided file path.
 *
 * Watches the file for changes so that each call of TagQueryProvider#getQueries returns the latest
 * tag queries from the configuration file.
 *
 * @author shane.xie
 */
@Slf4j
public class TagQueryProvider {

  private final CompletableFuture<Void> fileWatch;
  private final EventBus eventBus;
  private AtomicReference<List<TagQuery>> tagQueries;

  public TagQueryProvider(final Path tagSqlPath, EventBus eventBus) {
    tagQueries = new AtomicReference<>(parseTagSqlFile(tagSqlPath));
    fileWatch = startWatchingFile(tagSqlPath);
    this.eventBus = eventBus;
  }

  public List<TagQuery> getTagQueries() {
    return tagQueries.get();
  }

  public void stopWatching() {
    fileWatch.cancel(true);
  }

  // Blocking, only meant for use in tests
  @VisibleForTesting
  List<TagQuery> waitForQueryChange(final List<TagQuery> awaitedResult, Duration timeout) throws InterruptedException {
    long stopTime = System.currentTimeMillis() + timeout.toMillis();
    while (!tagQueries.get().equals(awaitedResult)) {
      if (System.currentTimeMillis() > stopTime) {
        throw new RuntimeException("Timeout");
      }
      Thread.sleep(50);
    }
    return tagQueries.get();
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
                  tagQueries.set(parseTagSqlFile(path));
                  eventBus.post(tagQueries.get());
                  break;

                case "ENTRY_DELETE":
                  tagQueries.set(ImmutableList.of());
                  break;

                default:
                  log.warn("Unexpected file watch event: {}", event.kind());
              }
            }
          }
          key.reset();
        }

      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Autoload failed for tag SQL configuration file", e);
      }
    }, Executors.newSingleThreadExecutor());
  }

  private List<TagQuery> parseTagSqlFile(final Path path) {
    try {
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml(new Constructor(TagQuery.class));
      final ImmutableList<TagQuery> queries = StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
          .map(query -> (TagQuery) query)
          .map(TagQueryProvider::enforceValid)
          .collect(ImmutableList.toImmutableList());

      // Fail early to give the operator a tight feedback loop when configuring the connector
      Preconditions.checkArgument(TagQuery.allUnique(queries), "Tag SQL queries must be unique");
      return queries;

    } catch (IOException ioe) {
      log.error("Failed to read tag SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }

  private static TagQuery enforceValid(final TagQuery query) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getName()), "Tag SQL query name is required");
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getInitialSyncMarker()),
        "Initial sync marker for tag SQL query %s can't be empty", query.getName());
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getSql()),
        "SQL is required for tag SQL query %s", query.getName());
    Preconditions.checkArgument(!query.getSkippedIds().isEmpty(),
        "Skipped ID list is required for tag SQL query %s. Use a sentinel value if none apply.",
        query.getName());
    return query;
  }
}
