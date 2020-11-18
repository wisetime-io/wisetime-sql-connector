/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.format;

import io.wisetime.connector.sql.sync.activity_type.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author shane.xie
 */
public class LogFormatter {

  public static String formatTags(final Collection<TagSyncRecord> tagSyncRecords) {
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

  public static String formatActivityTypes(final Collection<ActivityTypeRecord> activityTypeRecords) {
    return String.format("Synced %s code|label|description %s %s",
        activityTypeRecords.size(),
        activityTypeRecords.size() == 1 ? "tuple" : "tuples",
        ellipsize(
            activityTypeRecords.stream()
                .map(record -> record.getCode() + "|" + record.getLabel() + "|" + record.getDescription())
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
