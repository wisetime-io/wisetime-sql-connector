/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import lombok.Data;

/**
 * @author shane.xie
 */
@Data
public class TagSyncRecord {
  String reference;
  String tagName;
  String additionalKeyword;
  String tagDescription;
  String syncMarker;
}
