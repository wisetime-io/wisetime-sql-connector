/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.github.javafaker.Faker;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Generate random entities for tests.
 *
 * @author shane.xie
 */
public class RandomEntities {

  private static Faker faker = new Faker();
  public static Instant fixedInstant = Instant.now();

  public static TagSyncRecord randomTagSyncRecord() {
    final TagSyncRecord tagSyncRecord = new TagSyncRecord();
    tagSyncRecord.setReference(faker.numerify("#####"));
    tagSyncRecord.setTagName(faker.bothify("??######??").toUpperCase());
    tagSyncRecord.setKeyword(tagSyncRecord.getTagName());
    tagSyncRecord.setDescription(faker.lorem().characters(12, 35));
    tagSyncRecord.setSyncMarker(fixedTimeMinusMinutes(faker.random().nextInt(1, 120)));
    return tagSyncRecord;
  }

  public static TagSyncRecord randomTagSyncRecord(final String syncMarker) {
    final TagSyncRecord tagSyncRecord = randomTagSyncRecord();
    tagSyncRecord.setSyncMarker(syncMarker);
    return tagSyncRecord;
  }

  public static String fixedTime() {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant);
  }

  public static String fixedTimeMinusMinutes(final int minusMinutes) {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant.minus(minusMinutes, ChronoUnit.MINUTES));
  }
}
