/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import com.google.common.collect.ImmutableList;
import io.wisetime.generated.connect.UpsertTagRequest;
import lombok.Data;

/**
 * @author shane.xie
 */
@Data
public class TagSyncRecord {
  String reference;
  String tagName;
  String keyword;
  String description;
  String syncMarker;

  public UpsertTagRequest toUpsertTagRequest(final String path) {
    return new UpsertTagRequest()
        .name(tagName)

        // TODO(SX) If description is empty we don't want to overwrite data in WiseTime. Check behaviour.
        .description(description)

        .additionalKeywords(ImmutableList.of(keyword))
        .path(path);
  }
}
