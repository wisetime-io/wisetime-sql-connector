/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import static io.wisetime.connector.sql.format.LogFormatter.formatTags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.wisetime.connector.ConnectorModule;
import io.wisetime.connector.WiseTimeConnector;
import io.wisetime.connector.api_client.PostResult;
import io.wisetime.connector.sql.queries.ActivityTypeQuery;
import io.wisetime.connector.sql.queries.DrainRun;
import io.wisetime.connector.sql.queries.QueryProvider;
import io.wisetime.connector.sql.queries.TagQuery;
import io.wisetime.connector.sql.queries.TimesheetFieldOptionQuery;
import io.wisetime.connector.sql.sync.ConnectApi;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import io.wisetime.connector.sql.sync.TagSyncRecord;
import io.wisetime.connector.sql.sync.TagSyncStore;
import io.wisetime.connector.sql.sync.activity_type.ActivityTypeSyncService;
import io.wisetime.connector.sql.sync.activity_type.hash.ActivityTypeSyncWithHashService;
import io.wisetime.connector.sql.sync.activity_type.marker.ActivityTypeSyncWithMarkerService;
import io.wisetime.connector.sql.sync.timesheet_field.TimesheetFieldOptionSyncService;
import io.wisetime.connector.sql.sync.timesheet_field.hash.TimesheetFieldOptionSyncWithHashService;
import io.wisetime.connector.sql.sync.timesheet_field.marker.TimesheetFieldOptionSyncWithMarkerService;
import io.wisetime.generated.connect.TimeGroup;
import io.wisetime.generated.connect.TimesheetField;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author shane.xie
 */
@Slf4j
public class SqlConnector implements WiseTimeConnector {

  private final ConnectedDatabase database;
  private final QueryProvider<TagQuery> tagQueryProvider;
  private final QueryProvider<ActivityTypeQuery> activityTypeQueryProvider;
  private final QueryProvider<TimesheetFieldOptionQuery> timesheetFieldOptionQueryProvider;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TagSyncStore tagDrainSyncStore;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TagSyncStore tagRefreshSyncStore;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncService activityTypeSyncWithHashService;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private ActivityTypeSyncService activityTypeSyncWithMarkerService;

  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TimesheetFieldOptionSyncService timesheetFieldOptionSyncWithHashService;
  @VisibleForTesting
  @Setter(AccessLevel.PACKAGE)
  private TimesheetFieldOptionSyncService timesheetFieldOptionSyncWithMarkerService;

  private ConnectApi connectApi;
  private final AtomicBoolean isPerformingTagUpdate = new AtomicBoolean();
  private final AtomicBoolean isPerformingTagSlowResync = new AtomicBoolean();
  private final AtomicBoolean isPerformingActivityTypeSync = new AtomicBoolean();
  private final AtomicBoolean isPerformingActivityTypeSlowSync = new AtomicBoolean();
  private final AtomicBoolean isPerformingTimesheetFieldOptionSync = new AtomicBoolean();
  private final AtomicBoolean isPerformingTimesheetFieldOptionSlowSync = new AtomicBoolean();

  public SqlConnector(final ConnectedDatabase connectedDatabase,
      final QueryProvider<TagQuery> tagQueryProvider,
      final QueryProvider<ActivityTypeQuery> activityTypeQueryProvider,
      final QueryProvider<TimesheetFieldOptionQuery> timesheetFieldOptionQueryProvider) {
    database = connectedDatabase;
    this.tagQueryProvider = tagQueryProvider;
    this.tagQueryProvider.setListener(safeListener("tag", this::performTagUpdate));
    this.activityTypeQueryProvider = activityTypeQueryProvider;
    this.activityTypeQueryProvider.setListener(this::performActivityTypeUpdate);
    this.timesheetFieldOptionQueryProvider = timesheetFieldOptionQueryProvider;
    this.timesheetFieldOptionQueryProvider.setListener(
        safeListener("timesheet field option", this::performTimesheetFieldOptionUpdate));
  }

  /**
   * Wraps a file-change listener so a sync failure is logged instead of escaping: an uncaught exception here
   * would otherwise stop the underlying file watch loop for good, silently ignoring every later edit to the file.
   */
  private <T> QueryProvider.Listener<T> safeListener(String description, QueryProvider.Listener<T> listener) {
    return queries -> {
      try {
        listener.onQueriesUpdated(queries);
      } catch (RuntimeException e) {
        log.error("Failed to sync after {} SQL file change. Will retry on the next scheduled sync or file change.",
            description, e);
      }
    };
  }

  @Override
  public void init(ConnectorModule connectorModule) {
    tagDrainSyncStore = new TagSyncStore(connectorModule.getConnectorStore());
    tagRefreshSyncStore = new TagSyncStore(connectorModule.getConnectorStore(), "refresh");
    connectApi = new ConnectApi(connectorModule.getApiClient());
    activityTypeSyncWithHashService =
        new ActivityTypeSyncWithHashService(connectorModule.getConnectorStore(), connectApi, database);
    activityTypeSyncWithMarkerService =
        new ActivityTypeSyncWithMarkerService(connectorModule.getConnectorStore(), connectApi, database);
    timesheetFieldOptionSyncWithHashService =
        new TimesheetFieldOptionSyncWithHashService(connectorModule.getConnectorStore(), connectApi, database);
    timesheetFieldOptionSyncWithMarkerService =
        new TimesheetFieldOptionSyncWithMarkerService(connectorModule.getConnectorStore(), connectApi, database);
  }

  @Override
  public String getConnectorType() {
    return "wisetime-sql-connector";
  }

  @Override
  public void performTagUpdate() {
    performTagUpdate(tagQueryProvider.getQueries());
  }

  private void performTagUpdate(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTagUpdate.compareAndSet(false, true)) {
      try {
        tagQueries.forEach(query -> {
          final Supplier<Boolean> allowSync = () -> !hasUpdatedQueries(tagQueries);
          // Drain everything
          syncAllNewRecords(query, allowSync);
        });
      } finally {
        isPerformingTagUpdate.set(false);
      }
    }
  }

  @Override
  public void performTagUpdateSlowLoop() {
    performSlowResync(tagQueryProvider.getQueries());
  }

  private void performSlowResync(List<TagQuery> tagQueries) {
    if (tagQueries.isEmpty()) {
      log.warn("No tag SQL queries configured. Skipping tag sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTagSlowResync.compareAndSet(false, true)) {
      try {
        tagQueries.forEach(query -> {
          final Supplier<Boolean> allowSync = () -> !hasUpdatedQueries(tagQueries);
          // slow resync mechanism that is separate from the main drain-everything mechanism.
          refreshOneBatch(query, allowSync);
        });
      } finally {
        isPerformingTagSlowResync.set(false);
      }
    }
  }

  @Override
  public void performActivityTypeUpdate() {
    performActivityTypeUpdate(activityTypeQueryProvider.getQueries());
  }

  private void performActivityTypeUpdate(List<ActivityTypeQuery> activityTypeQueries) {
    if (activityTypeQueries.isEmpty()) {
      log.warn("No activity type SQL queries configured. Skipping activity types sync.");
      return;
    }
    Preconditions.checkArgument(activityTypeQueries.size() == 1, "At most one activity type SQL query must be provided");
    final ActivityTypeQuery query = activityTypeQueries.get(0);

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingActivityTypeSync.compareAndSet(false, true)) {
      try {
        getActivityTypeSyncService(query)
            .performActivityTypeUpdate(query);
      } finally {
        isPerformingActivityTypeSync.set(false);
      }
    }
  }

  @Override
  public void performActivityTypeUpdateSlowLoop() {
    performActivityTypeUpdateSlowLoop(activityTypeQueryProvider.getQueries());
  }

  private void performActivityTypeUpdateSlowLoop(List<ActivityTypeQuery> activityTypeQueries) {
    if (activityTypeQueries.isEmpty()) {
      log.warn("No activity type SQL queries configured. Skipping activity types slow loop sync.");
      return;
    }
    Preconditions.checkArgument(activityTypeQueries.size() == 1, "At most one activity type SQL query must be provided");
    final ActivityTypeQuery query = activityTypeQueries.get(0);

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingActivityTypeSlowSync.compareAndSet(false, true)) {
      try {
        getActivityTypeSyncService(query)
            .performActivityTypeUpdateSlowLoop(query);
      } finally {
        isPerformingActivityTypeSlowSync.set(false);
      }
    }
  }

  private ActivityTypeSyncService getActivityTypeSyncService(ActivityTypeQuery query) {
    return query.hasSyncMarker() ? activityTypeSyncWithMarkerService : activityTypeSyncWithHashService;
  }

  @Override
  public void performTimesheetFieldOptionUpdate() {
    performTimesheetFieldOptionUpdate(timesheetFieldOptionQueryProvider.getQueries());
  }

  private void performTimesheetFieldOptionUpdate(List<TimesheetFieldOptionQuery> timesheetFieldOptionQueries) {
    if (timesheetFieldOptionQueries.isEmpty()) {
      log.debug("No timesheet field option SQL queries configured. Skipping timesheet field option sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTimesheetFieldOptionSync.compareAndSet(false, true)) {
      try {
        syncEachTimesheetField(connectedTimesheetFieldOptionQueries(timesheetFieldOptionQueries),
            query -> getTimesheetFieldOptionSyncService(query).performTimesheetFieldOptionUpdate(query));
      } finally {
        isPerformingTimesheetFieldOptionSync.set(false);
      }
    }
  }

  @Override
  public void performTimesheetFieldOptionUpdateSlowLoop() {
    performTimesheetFieldOptionUpdateSlowLoop(timesheetFieldOptionQueryProvider.getQueries());
  }

  private void performTimesheetFieldOptionUpdateSlowLoop(List<TimesheetFieldOptionQuery> timesheetFieldOptionQueries) {
    if (timesheetFieldOptionQueries.isEmpty()) {
      log.debug("No timesheet field option SQL queries configured. Skipping timesheet field option slow loop sync.");
      return;
    }

    // Prevent possible concurrent runs of scheduled update and on query changed event
    if (isPerformingTimesheetFieldOptionSlowSync.compareAndSet(false, true)) {
      try {
        syncEachTimesheetField(connectedTimesheetFieldOptionQueries(timesheetFieldOptionQueries),
            query -> getTimesheetFieldOptionSyncService(query).performTimesheetFieldOptionUpdateSlowLoop(query));
      } finally {
        isPerformingTimesheetFieldOptionSlowSync.set(false);
      }
    }
  }

  private TimesheetFieldOptionSyncService getTimesheetFieldOptionSyncService(TimesheetFieldOptionQuery query) {
    return query.hasSyncMarker() ? timesheetFieldOptionSyncWithMarkerService : timesheetFieldOptionSyncWithHashService;
  }

  /**
   * Syncs every field independently: a failure syncing one field is logged and does not prevent the remaining
   * fields from being attempted. Once all fields have been attempted, if any failed, the first failure is
   * rethrown with every other failure attached to it via {@link Throwable#addSuppressed}, so the run is still
   * reported as failed, e.g. for the base library's connector health check, without losing the other failures.
   */
  private void syncEachTimesheetField(
      List<TimesheetFieldOptionQuery> timesheetFieldOptionQueries, Consumer<TimesheetFieldOptionQuery> sync) {
    RuntimeException firstFailure = null;
    for (final TimesheetFieldOptionQuery query : timesheetFieldOptionQueries) {
      try {
        sync.accept(query);
      } catch (RuntimeException e) {
        log.error("Failed to sync timesheet field options for field '{}'", query.getFieldId(), e);
        if (firstFailure == null) {
          firstFailure = e;
        } else {
          firstFailure.addSuppressed(e);
        }
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
  }

  /**
   * Filters out configured queries whose field ID is not a connected WiseTime timesheet field, so a stale or
   * mistyped field ID, or a custom (non-connector) field, doesn't fail the whole sync run.
   */
  private List<TimesheetFieldOptionQuery> connectedTimesheetFieldOptionQueries(
      List<TimesheetFieldOptionQuery> timesheetFieldOptionQueries) {
    final List<TimesheetField> fields = connectApi.listTimesheetFields();
    final Set<String> allFieldIds = fields.stream()
        .map(TimesheetField::getId)
        .collect(Collectors.toSet());
    final Set<String> connectedFieldIds = fields.stream()
        .filter(field -> Boolean.TRUE.equals(field.getConnected()))
        .map(TimesheetField::getId)
        .collect(Collectors.toSet());

    return timesheetFieldOptionQueries.stream()
        .filter(query -> {
          final String fieldId = query.getFieldId();
          if (connectedFieldIds.contains(fieldId)) {
            return true;
          }
          if (allFieldIds.contains(fieldId)) {
            log.warn("Configured timesheet field '{}' is a custom WiseTime field, not one connected to this "
                + "connector. Skipping sync for this field.", fieldId);
          } else {
            log.warn("Configured timesheet field '{}' is not a connected WiseTime timesheet field. "
                + "Skipping sync for this field.", fieldId);
          }
          return false;
        })
        .collect(Collectors.toList());
  }

  @Override
  public PostResult postTime(TimeGroup timeGroup) {
    throw new UnsupportedOperationException("Time posting is not supported by the WiseTime SQL Connector");
  }

  @Override
  public boolean isConnectorHealthy() {
    return tagQueryProvider.isHealthy()
        && activityTypeQueryProvider.isHealthy()
        && timesheetFieldOptionQueryProvider.isHealthy()
        && database.isAvailable();
  }

  @Override
  public void shutdown() {
    database.close();
    tagQueryProvider.stop();
    timesheetFieldOptionQueryProvider.stop();
  }

  @VisibleForTesting
  void syncAllNewRecords(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    new DrainRun<>(
        allowSync,
        () -> getUnsyncedRecords(tagQuery, tagDrainSyncStore),
        newBatch -> {
          Preconditions.checkArgument(newBatch instanceof LinkedList);
          connectApi.upsertWiseTimeTags(newBatch);
          tagDrainSyncStore.markSyncPosition(tagQuery, (LinkedList<TagSyncRecord>) newBatch);
          log.info("New tag detection: " + formatTags(newBatch));
        }).run();
  }

  @VisibleForTesting
  void refreshOneBatch(final TagQuery tagQuery, final Supplier<Boolean> allowSync) {
    if (!tagQuery.getContinuousResync() || !allowSync.get()) {
      return;
    }
    final LinkedList<TagSyncRecord> refreshTagSyncRecords = getUnsyncedRecords(tagQuery, tagRefreshSyncStore);
    if (refreshTagSyncRecords.isEmpty()) {
      // Next refresh batch to start again from the beginning
      log.info("Resetting tag refresh to start from the beginning");
      tagRefreshSyncStore.resetSyncPosition(tagQuery);
      return;
    }
    connectApi.upsertWiseTimeTags(refreshTagSyncRecords);
    tagRefreshSyncStore.markSyncPosition(tagQuery, refreshTagSyncRecords);
    log.info("Existing tag refresh: " + formatTags(refreshTagSyncRecords));
  }

  @VisibleForTesting
  void setConnectApi(final ConnectApi connectApi) {
    this.connectApi = connectApi;
  }

  private LinkedList<TagSyncRecord> getUnsyncedRecords(final TagQuery query, final TagSyncStore syncStore) {
    final String syncMarker = syncStore.getSyncMarker(query);

    final List<String> idsToSkip = Stream
        .concat(query.getSkippedIds().stream(), syncStore.getLastSyncedIds(query).stream())
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());

    return database.getTagsToSync(query.getSql(), syncMarker, idsToSkip);
  }

  private boolean hasUpdatedQueries(final List<TagQuery> tagQueries) {
    return !tagQueries.equals(tagQueryProvider.getQueries());
  }
}
