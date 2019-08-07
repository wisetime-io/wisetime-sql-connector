/*
 * Copyright (c) 2019 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.wisetime.connector.ConnectorController;
import io.wisetime.connector.config.RuntimeConfig;
import io.wisetime.connector.config.RuntimeConfigKey;
import io.wisetime.connector.sql.queries.TagQueryProvider;
import io.wisetime.connector.sql.sync.ConnectedDatabase;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * SQL Connector application entry point.
 *
 * @author shane.xie
 */
public class ConnectorLauncher {

  public static void main(final String... args) throws Exception {
    ConnectorController connectorController = buildConnectorController();
    connectorController.start();
  }

  public static ConnectorController buildConnectorController() {
    final ConnectedDatabase database = new ConnectedDatabase(buildDataSource());

    final Path tagSqlPath = Paths.get(
        RuntimeConfig.getString(SqlConnectorConfigKey.TAG_SQL_FILE)
            .orElseThrow(() -> new RuntimeException("Missing TAG_SQL_FILE configuration"))
    );
    final TagQueryProvider tagQueryProvider = new TagQueryProvider(tagSqlPath);

    return ConnectorController.newBuilder()
        .withWiseTimeConnector(new SqlConnector(database, tagQueryProvider))
        // This connector does not process posted time
        .useTagsOnly()
        .build();
  }

  /**
   * Configuration keys for the WiseTime SQL Connector.
   */
  public enum SqlConnectorConfigKey implements RuntimeConfigKey {
    JDBC_URL("JDBC_URL"),
    JDBC_USER("JDBC_USER"),
    JDBC_PASSWORD("JDBC_PASSWORD"),
    TAG_UPSERT_PATH("TAG_UPSERT_PATH"),
    TAG_SQL_FILE("TAG_SQL_FILE");

    private final String configKey;

    SqlConnectorConfigKey(final String configKey) {
      this.configKey = configKey;
    }

    @Override
    public String getConfigKey() {
      return configKey;
    }
  }

  private static HikariDataSource buildDataSource() {
    final HikariConfig hikariConfig = new HikariConfig();

    hikariConfig.setJdbcUrl(
        RuntimeConfig.getString(SqlConnectorConfigKey.JDBC_URL)
            .orElseThrow(() -> new RuntimeException("Missing required JDBC_URL configuration"))
    );
    hikariConfig.setUsername(
        RuntimeConfig.getString(SqlConnectorConfigKey.JDBC_USER)
            .orElseThrow(() -> new RuntimeException("Missing required JDBC_USER configuration"))
    );
    hikariConfig.setPassword(
        RuntimeConfig.getString(SqlConnectorConfigKey.JDBC_PASSWORD)
            .orElseThrow(() -> new RuntimeException("Missing required JDBC_PASSWORD configuration"))
    );
    hikariConfig.setConnectionTimeout(TimeUnit.MINUTES.toMillis(1));
    hikariConfig.setMaximumPoolSize(10);
    hikariConfig.setReadOnly(true);

    return new HikariDataSource(hikariConfig);
  }
}
