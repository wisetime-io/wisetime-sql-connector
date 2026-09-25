/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * @author yehor.lashkul
 */
class NoOpQueryProviderTest {

  private final QueryProvider<ActivityTypeQuery> noOpQueryProvider = QueryProvider.noOp();

  @Test
  void test() {
    assertThat(noOpQueryProvider.isHealthy())
        .as("always healthy")
        .isTrue();

    assertThat(noOpQueryProvider.getQueries())
        .as("there is always no queries")
        .isEmpty();

    assertThatCode(() -> noOpQueryProvider.setListener(null))
        .as("just does nothing")
        .doesNotThrowAnyException();

    assertThatCode(noOpQueryProvider::stop)
        .as("just does nothing")
        .doesNotThrowAnyException();
  }

}
