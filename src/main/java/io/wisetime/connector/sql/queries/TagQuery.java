/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author shane.xie
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagQuery {
  private String name;
  private String sql;
  private String initialSyncMarker;

  // Comma separated list of IDs to skip
  private List<String> skippedIds;
}
