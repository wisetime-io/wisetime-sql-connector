/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

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
  private List<TagQuery> tagQueries;

  public TagQueryProvider(final Path tagSqlPath) {
    tagQueries = parseTagSqlFile(tagSqlPath);
    fileWatch = startWatchingFile(tagSqlPath);
  }

  public List<TagQuery> getQueries() {
    return ImmutableList.copyOf(tagQueries);
  }

  public void stopWatching() {
    fileWatch.cancel(true);
  }

  public static boolean hasUniqueQueryNames(final List<TagQuery> tagQueries) {
    final Set<String> uniqueQueryNames = tagQueries.stream().map(TagQuery::getName).collect(Collectors.toSet());
    return tagQueries.size() == uniqueQueryNames.size();
  }

  // Blocking, only meant for use in tests
  @VisibleForTesting
  List<TagQuery> waitForQueryChange(final List<TagQuery> awaitedResult, Duration timeout) throws InterruptedException {
    long stopTime = System.currentTimeMillis() + timeout.toMillis();
    while (!tagQueries.equals(awaitedResult)) {
      if (System.currentTimeMillis() > stopTime) {
        throw new RuntimeException("Timeout");
      }
      Thread.sleep(50);
    }
    return tagQueries;
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
                  tagQueries = parseTagSqlFile(path);
                  break;

                case "ENTRY_DELETE":
                  tagQueries = ImmutableList.of();
                  break;

                default:
                  log.warn("Unexpected file watch event: {}", event.kind());
              }
            }
          }
          key.reset();
        }

      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Autoload failed for Tag SQL configuration file", e);
      }
    }, Executors.newSingleThreadExecutor());
  }

  private List<TagQuery> parseTagSqlFile(final Path path) {
    try {
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml(new Constructor(TagQuery.class));
      final List<TagQuery> queries = StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
          .map(query -> (TagQuery) query)
          .map(this::enforceValid)
          .collect(Collectors.toList());

      // Fail early to give the operator a tight feedback loop when configuring the connector
      Preconditions.checkArgument(hasUniqueQueryNames(queries), "Tag SQL query names must be unique");
      return queries;

    } catch (IOException ioe) {
      log.error("Failed to read tag SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }

  private TagQuery enforceValid(final TagQuery query) {
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getName()), "query name is required");
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getInitialSyncMarker()),
        "initial marker of query %s can't be empty", query.getName());
    Preconditions.checkArgument(StringUtils.isNotEmpty(query.getSql()),
        "sql is required for query %s", query.getName());
    Preconditions.checkArgument(!query.getSkippedIds().isEmpty(),
        "skipped ids required for query %s", query.getName());
    return query;
  }
}
