/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import io.vavr.control.Try;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.codejargon.fluentjdbc.api.query.Mapper;

/**
 * @author shane.xie
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
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

  public static Mapper<TagSyncRecord> fluentJdbcMapper() {
    return resultSet -> new TagSyncRecord()
        .setId(resultSet.getString("id"))
        .setTagName(resultSet.getString("tag_name"))
        .setTagMetadata(Try.of(() -> resultSet.getString("tag_metadata")).getOrElse("{}"))
        .setAdditionalKeyword(resultSet.getString("additional_keyword"))
        .setTagDescription(resultSet.getString("tag_description"))
        .setSyncMarker(resultSet.getString("sync_marker"));
  }
}
