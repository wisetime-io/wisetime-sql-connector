/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync;

import io.vavr.control.Try;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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
  // JSON object of tag attributes (values may be any JSON value, e.g. lists of option ids). Null if the tag query has
  // no `tag_attributes` column, in which case attributes are not sent and remain unchanged in WiseTime. When the
  // column exists, an empty value means the tag has no attributes and clears any existing ones.
  private String tagAttributes;
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
        .setTagAttributes(hasColumn(resultSet, "tag_attributes")
            ? readTagAttributes(resultSet)
            : null)
        .setAdditionalKeyword(resultSet.getString("additional_keyword"))
        .setTagDescription(resultSet.getString("tag_description"))
        .setSyncMarker(resultSet.getString("sync_marker"));
  }

  public Optional<String> getTagAttributes() {
    return Optional.ofNullable(tagAttributes);
  }

  private static String readTagAttributes(ResultSet resultSet) throws SQLException {
    // A read failure is not swallowed: defaulting would either clear the tag's attributes in WiseTime or silently leave
    // them stale. Like the other required columns, it fails the sync until the query is fixed.
    return Optional.ofNullable(resultSet.getString("tag_attributes"))
        .filter(it -> !it.isBlank())
        .orElse("{}");
  }

  private static boolean hasColumn(ResultSet resultSet, String column) throws SQLException {
    ResultSetMetaData metaData = resultSet.getMetaData();
    for (int i = 1; i <= metaData.getColumnCount(); i++) {
      if (column.equalsIgnoreCase(metaData.getColumnLabel(i))) {
        return true;
      }
    }
    return false;
  }
}
