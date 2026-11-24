/*
 * Copyright (c) 2020 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql.sync.activity_type;

import io.wisetime.connector.sql.queries.ActivityTypeQuery;

/**
 * @author yehor.lashkul
 */
public interface ActivityTypeSyncService {

  void performActivityTypeUpdate(ActivityTypeQuery activityTypeQuery);

  void performActivityTypeUpdateSlowLoop(ActivityTypeQuery activityTypeQuery);

}
