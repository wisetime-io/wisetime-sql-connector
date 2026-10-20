/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import io.vavr.control.Try;
import java.util.Optional;
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
  private String url;
  private String externalId;
  @NonNull
  private String tagMetadata;
  @NonNull
  private String syncMarker;

  public static Mapper<TagSyncRecord> fluentJdbcMapper() {
    return resultSet -> new TagSyncRecord()
        .setId(resultSet.getString("id"))
        .setTagName(resultSet.getString("tag_name"))
        .setUrl(Try.of(() -> resultSet.getString("url")).getOrNull())
        .setExternalId(Try.of(() -> resultSet.getString("external_id")).getOrNull())
        .setTagMetadata(
            Try.of(() -> Optional.ofNullable(resultSet.getString("tag_metadata")).orElse("{}"))
                .getOrElse("{}"))
        .setAdditionalKeyword(resultSet.getString("additional_keyword"))
        .setTagDescription(resultSet.getString("tag_description"))
        .setSyncMarker(resultSet.getString("sync_marker"));
  }
}
