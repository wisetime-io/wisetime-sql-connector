/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.format;

import static io.wisetime.connector.sql.RandomEntities.randomTagSyncRecord;
import static io.wisetime.connector.sql.format.LogFormatter.format;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import org.junit.jupiter.api.Test;

/**
 * @author shane.xie
 */
class LogFormatterTest {

  @Test
  void format_no_records_synced() {
    assertThat(format(ImmutableList.of())).isEqualTo("Synced 0 tag|keyword pairs");
  }

  @Test
  void format_one_record_synced() {
    final TagSyncRecord record = randomTagSyncRecord();
    assertThat(format(ImmutableList.of(record)))
        .isEqualTo("Synced 1 tag|keyword pair " + record.getTagName() + "|" + record.getAdditionalKeyword());
  }

  @Test
  void format_multiple_all_listed() {
    final TagSyncRecord record1 = randomTagSyncRecord();
    final TagSyncRecord record2 = randomTagSyncRecord();

    assertThat(format(ImmutableList.of(record1, record2)))
        .isEqualTo("Synced 2 tag|keyword pairs " + record1.getTagName() + "|"
            + record1.getAdditionalKeyword()
            + ", " + record2.getTagName() + "|" + record2.getAdditionalKeyword());
  }

  @Test
  void format_multiple_ellipsized() {
    final TagSyncRecord first = randomTagSyncRecord();
    final TagSyncRecord last = randomTagSyncRecord();

    assertThat(format(ImmutableList.of(
        first,
        randomTagSyncRecord(),
        randomTagSyncRecord(),
        randomTagSyncRecord(),
        randomTagSyncRecord(),
        last
      ))
    ).isEqualTo("Synced 6 tag|keyword pairs " + first.getTagName() + "|" + first.getAdditionalKeyword()
        + ", ... , " + last.getTagName() + "|" + last.getAdditionalKeyword());
  }
}