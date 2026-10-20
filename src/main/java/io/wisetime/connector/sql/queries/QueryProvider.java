/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.queries;

import java.util.Collections;
import java.util.List;

/**
 * @author yehor.lashkul
 */
public interface QueryProvider<T> {

  List<T> getQueries();

  void setListener(Listener<T> listener);

  void stop();

  boolean isHealthy();

  static <T> QueryProvider<T> noOp() {
    return new QueryProvider<T>() {
      @Override
      public List<T> getQueries() {
        return Collections.emptyList();
      }

      @Override
      public void setListener(Listener<T> listener) {
        // nothing to listen to
      }

      @Override
      public void stop() {
        // nothing to stop
      }

      @Override
      public boolean isHealthy() {
        return true;
      }
    };
  }

  interface Listener<T> {

    void onQueriesUpdated(List<T> queries);

    static <T> FileWatchQueryProvider.Listener<T> noOp() {
      return queries -> {
        // do nothing
      };
    }
  }
}
