/*
 * Copyright (c) 2025 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import static io.wisetime.connector.sql.format.LogFormatter.ellipsize;

import java.util.List;
import lombok.Getter;

/**
 * Thrown after a sync run when some tags were left out. All the other tags have been synced.
 */
public class TagSyncException extends RuntimeException {

  @Getter
  private final List<String> failedTagIds;

  public TagSyncException(List<String> failedTagIds) {
    super(failedTagIds.size() + " tag(s) were not synced, all other tags were synced. They are tried again when their "
        + "sync marker changes, or by the slow resync if continuousResync is enabled. The reasons are logged as warnings. "
        + "Tag ids: " + ellipsize(failedTagIds));
    this.failedTagIds = List.copyOf(failedTagIds);
  }
}
