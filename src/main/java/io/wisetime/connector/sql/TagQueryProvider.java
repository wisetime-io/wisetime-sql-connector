/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads tag queries from the provided file path.
 *
 * Watches the file for changes so that each call of TagQueryProvider#getQueries returns the latest
 * tag queries from the configuration file.
 *
 * @author shane.xie
 */
@Singleton
@Slf4j
class TagQueryProvider {

  private CompletableFuture<Void> fileWatch;
  private List<TagQuery> tagQueries;

  TagQueryProvider(final Path tagSqlPath) {
    tagQueries = parseTagSqlFile(tagSqlPath);
    fileWatch = startWatchingFile(tagSqlPath);
  }

  List<TagQuery> getQueries() {
    return tagQueries;
  }

  void stopWatching() {
    fileWatch.cancel(true);
  }

  private CompletableFuture<Void> startWatchingFile(final Path path) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final WatchService watchService = FileSystems.getDefault().newWatchService();

        path.register(watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey key;
        while ((key = watchService.take()) != null) {
          for (WatchEvent<?> event : key.pollEvents()) {
            switch (event.kind().name()) {
              case "ENTRY_CREATE":
              case "ENTRY_MODIFY":
                tagQueries = parseTagSqlFile(path);

              case "ENTRY_DELETE":
                log.error("The tag SQL configuration file {} was deleted", path);
                tagQueries = ImmutableList.of();
                continue;

              default:
                log.error("Unexpected file watch event");
                continue;
            }
          }
          key.reset();
        }

      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Autoload failed for Tag SQL configuration file", e);
      }
      return null;
    });
  }

  private List<TagQuery> parseTagSqlFile(final Path path) {
    try {
      final Stream<String> lines = Files.lines(path);
      final String contents = lines.collect(Collectors.joining("\n"));
      lines.close();

      final Yaml yaml = new Yaml();
      final Map<String, String> namedQueries = yaml.load(contents);
      if (namedQueries == null) {
        log.error("Tag SQL configuration file {} is empty or invalid", path);
        return ImmutableList.of();
      }

      return namedQueries
          .entrySet()
          .stream()
          .map(entry -> {
            final TagQuery tagQuery = new TagQuery();
            tagQuery.setName(entry.getKey());
            tagQuery.setSql(entry.getValue());
            return tagQuery;
          })
          .collect(Collectors.toList());

    } catch (IOException ioe) {
      log.error("Failed to read tag SQL configuration file at {}", path);
      return ImmutableList.of();
    }
  }
}
