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

  public static SyncItem randomSyncItem() {
    final SyncItem item = new SyncItem();
    item.setReference(faker.numerify("#####"));
    item.setTagName(faker.bothify("??######??").toUpperCase());
    item.setKeyword(item.getTagName());
    item.setDescription(faker.lorem().characters(12, 35));
    item.setSyncMarker(fixedTimeMinusMinutes(faker.random().nextInt(1, 120)));
    return item;
  }

  public static SyncItem randomSyncItem(final String syncMarker) {
    final SyncItem item = randomSyncItem();
    item.setSyncMarker(syncMarker);
    return item;
  }

  public static String fixedTime() {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant);
  }

  public static String fixedTimeMinusMinutes(final int minusMinutes) {
    return DateTimeFormatter.ISO_INSTANT.format(fixedInstant.minus(minusMinutes, ChronoUnit.MINUTES));
  }
}
