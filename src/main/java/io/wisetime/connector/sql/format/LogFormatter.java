/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.format;

import io.wisetime.connector.sql.sync.TagSyncRecord;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author shane.xie
 */
public class LogFormatter {

  public static String format(final Collection<TagSyncRecord> tagSyncRecords) {
    return String.format("Synced %s tag|keyword %s %s",
        tagSyncRecords.size(),
        tagSyncRecords.size() == 1 ? "pair" : "pairs",
        ellipsize(
            tagSyncRecords.stream()
                .map(record -> record.getTagName() + "|" + record.getAdditionalKeyword())
                .collect(Collectors.toList())
        )
    ).trim();
  }

  public static String ellipsize(final List<String> items) {
    if (items.size() < 6) {
      return String.join(", ", items);
    }
    return items.get(0) + ", ... , " + items.get(items.size() - 1);
  }
}
