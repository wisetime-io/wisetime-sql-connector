/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * @author shane.xie
 */
@Data
@NoArgsConstructor
public class TagSyncRecord {
  @NonNull
  private String id;
  @NonNull
  private String tagName;
  private String additionalKeyword;
  private String tagDescription;
  @NotNull
  private String tagMetadata;
  @NonNull
  private String syncMarker;
}
