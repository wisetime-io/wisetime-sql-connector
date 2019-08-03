/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.google.common.annotations.VisibleForTesting;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.ApiClient;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.sql.ConnectorLauncher.SqlConnectorConfigKey;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.SyncStore;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import io.wisetime.generated.connect.TimeGroup;
import io.wisetime.generated.connect.UpsertTagRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import spark.Request;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private static final String TAG_UPSERT_PATH = RuntimeConfig.getString(SqlConnectorConfigKey.TAG_UPSERT_PATH)
      .orElseThrow(() -> new RuntimeException("Missing required TAG_UPSERT_PATH configuration"));

  private ConnectedDatabase database;
  private TagQueryProvider tagQueryProvider;
  private SyncStore syncStore;
  private ApiClient apiClient;

  public SqlConnector(final ConnectedDatabase connectedDatabase, final TagQueryProvider tagQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    syncStore = new SyncStore(connectorModule.getConnectorStore());
    apiClient = connectorModule.getApiClient();
  }

  @Override
  public void performTagUpdate() {
    tagQueryProvider.getQueries().stream()
        .forEachOrdered(query -> {
          final String marker = syncStore.getSyncMarker(query.getName(), query.getInitialSyncMarker());
          final List<String> lastSyncedReferences = syncStore.getLastSyncedReferences(query.getName());
          Collection<TagSyncRecord> tagSyncRecords;

          while ((tagSyncRecords = database.getTagsToSync(query.getSql(), marker, lastSyncedReferences)).size() > 0) {
            upsertWiseTimeTags(tagSyncRecords, TAG_UPSERT_PATH);
            syncStore.markSyncPosition(query.getName(), tagSyncRecords);
            log.info(formatForLog(tagSyncRecords));
          }
        });
  }

  @Override
  public PostResult postTime(Request request, TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    return !tagQueryProvider.getQueries().isEmpty() && database.isAvailable();
  }

  @Override
  public void shutdown() {
    database.close();
    tagQueryProvider.stopWatching();
  }

  private void upsertWiseTimeTags(Collection<TagSyncRecord> tagSyncRecords, final String path) {
    final List<UpsertTagRequest> requests = tagSyncRecords.stream()
        .map(tagSyncRecord -> tagSyncRecord.toUpsertTagRequest(path))
        .collect(Collectors.toList());
    if (!requests.isEmpty()) {
      try {
        apiClient.tagUpsertBatch(requests);
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }
  }

  @VisibleForTesting
  String formatForLog(final Collection<TagSyncRecord> tagSyncRecords) {
    return String.format("Upserting %s %s: $s",
        tagSyncRecords.size(),
        tagSyncRecords.size() > 1 ? "[tag|keyword]s" : "[tag|keyword]",
        ellipsize(
            tagSyncRecords.stream()
                .map(record -> record.getTagName() + "|" + record.getKeyword())
                .collect(Collectors.toList())
        )
    );
  }

  private String ellipsize(final List<String> items) {
    if (items.size() == 0) {
      return "";
    }
    if (items.size() == 1) {
      return items.get(0);
    }
    if (items.size() < 6) {
      return items.stream().collect(Collectors.joining(", "));
    }
    return items.get(0) + ", ... , " + items.get(items.size() - 1);
  }
}
