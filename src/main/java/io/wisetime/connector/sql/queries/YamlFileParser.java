/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * @author yehor.lashkul
 */
@RequiredArgsConstructor
class YamlFileParser<T> {

  private final Class<T> rootObject;

  Stream<T> parse(Path path) throws IOException {
    final String contents = readFile(path);
    final Yaml yaml = new Yaml(new Constructor(rootObject));
    return StreamSupport.stream(yaml.loadAll(contents).spliterator(), false)
        .map(rootObject::cast);
  }

  private String readFile(Path path) throws IOException {
    try (final Stream<String> lines = Files.lines(path)) {
      return lines.collect(Collectors.joining("\n"));
    }
  }

}
