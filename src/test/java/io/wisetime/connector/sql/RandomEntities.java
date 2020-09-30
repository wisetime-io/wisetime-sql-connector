/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.github.javafaker.Faker;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.sync.ActivityTypeRecord;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

/**
 * Generate random entities for tests.
 *
 * @author shane.xie
 */
public class RandomEntities {

  private static Faker faker = new Faker();
  public static Instant fixedInstant = Instant.now();
  private static Gson gson = new Gson();

  public static TagSyncRecord randomTagSyncRecord() {
    final TagSyncRecord tagSyncRecord = new TagSyncRecord();
    tagSyncRecord.setId(faker.numerify("#####"));
    tagSyncRecord.setTagName(faker.bothify("??######??").toUpperCase());
    tagSyncRecord.setAdditionalKeyword(tagSyncRecord.getTagName());
    tagSyncRecord.setTagMetadata(
        gson.toJson(ImmutableMap.of("name", faker.company().name(), "url", faker.company().url())));
    tagSyncRecord.setTagDescription(faker.lorem().characters(12, 35));
    tagSyncRecord.setSyncMarker(fixedTimeMinusMinutes(faker.random().nextInt(1, 120)));
    return tagSyncRecord;
  }

  public static TagSyncRecord randomTagSyncRecord(final String syncMarker) {
    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();
    tagSyncRecord.setSyncMarker(syncMarker);
    return tagSyncRecord;
  }

  public static ActivityTypeRecord randomActivityTypeRecord() {
    return new ActivityTypeRecord(
        faker.numerify("code-###"),
        faker.numerify("description-###"));
  }

  public static String fixedTime() {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant);
  }

  public static String fixedTimeMinusMinutes(final int minusMinutes) {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant.minus(minusMinutes, ChronoUnit.MINUTES));
  }

  public static TagQuery randomTagQuery(String name) {
    return randomTagQuery(name, faker.bothify("??####"));
  }

  public static TagQuery randomTagQuery(String name, String syncMarker) {
    TagQuery tagQuery = new TagQuery();
    tagQuery.setName(name);
    tagQuery.setSql("SELECT '" + faker.lorem().sentence() + "'");
    tagQuery.setInitialSyncMarker(syncMarker);
    tagQuery.setSkippedIds(Collections.emptyList());
    tagQuery.setContinuousResync(faker.random().nextBoolean());
    return tagQuery;
  }
}
