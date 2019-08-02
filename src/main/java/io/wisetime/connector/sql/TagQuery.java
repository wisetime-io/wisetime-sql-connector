/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author shane.xie
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
class TagQuery {
  String name;
  String sql;
}
