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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
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

  private CompletableFuture<Void> fileWatch;
  private List<TagQuery> tagQueries;

  public TagQueryProvider(final Path tagSqlPath) {
    tagQueries = parseTagSqlFile(tagSqlPath);
    fileWatch = startWatchingFile(tagSqlPath);
  }

  public List<TagQuery> getQueries() {
    return tagQueries;
  }

  public void stopWatching() {
    fileWatch.cancel(true);
  }

  // Blocking, only meant for use in tests
  @VisibleForTesting
  List<TagQuery> waitForQueryChange(final List<TagQuery> awaitedResult) throws InterruptedException {
    while (!tagQueries.equals(awaitedResult)) {
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
                  log.error("The tag SQL configuration file {} was deleted", path);
                  tagQueries = ImmutableList.of();
                  break;

                default:
                  log.error("Unexpected file watch event");
              }
            }
          }
          key.reset();
        }

      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Autoload failed for Tag SQL configuration file", e);
      }
    });
  }

  private List<TagQuery> parseTagSqlFile(final Path path) {
    try {
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml(new Constructor(TagQuery.class));
      final List<TagQuery> queries = StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
          .map(query -> (TagQuery) query)
          .collect(Collectors.toList());

      final Set<String> uniqueQueryNames = queries.stream().map(TagQuery::getName).collect(Collectors.toSet());
      Preconditions.checkArgument(queries.size() == uniqueQueryNames.size(),
          "SQL query names must be unique");

      return queries;

    } catch (IOException ioe) {
      log.error("Failed to read tag SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }
}
