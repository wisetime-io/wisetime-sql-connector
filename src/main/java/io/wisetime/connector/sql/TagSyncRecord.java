/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import lombok.Data;

/**
 * @author shane.xie
 */
@Data
class TagSyncRecord {
  String reference;
  String tagName;
  String keyword;
  String description;
  String syncMarker;
}
