/*
 * Copyright (c) 2021 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.connector.sql;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.wisetime.test_docker.ContainerDefinition;
import io.wisetime.test_docker.ContainerRuntimeSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thomas.haines
 */
@SuppressWarnings("WeakerAccess")
public class PlainSqlServer implements ContainerDefinition {
  private static final Logger log = LoggerFactory.getLogger(PlainSqlServer.class);
  static final Integer MS_SQL_SERVER_PORT = 1433;

  @Override
  public String getImageId() {
    return "mcr.microsoft.com/azure-sql-edge:1.0.7";
  }

  @Override
  public String getShortName() {
    return "mssql";
  }

  @Override
  public Function<ContainerRuntimeSpec, Boolean> containerReady() {
    return sqlServerContainerReady();
  }

  /**
   * SQL Server rejects every login, including SA, while it is still starting up, so a rejected login does not
   * indicate readiness. The container is ready once SA can log in and run a query.
   */
  public Function<ContainerRuntimeSpec, Boolean> sqlServerContainerReady() {
    return container -> {
      final String className = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
      try {
        Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(
            String.format("Please add sqlserver jdbc driver (%s) to your classpath", className)
        );
      }

      String jdbcUrl = getJdbcDefaultPort(container);
      try (Connection connection = DriverManager.getConnection(jdbcUrl, getUsername(), getPassword());
          Statement statement = connection.createStatement()) {
        statement.execute("SELECT 1");
        log.info("SQLServer is ready to accept connections");
        return true;
      } catch (SQLException sqlException) {
        log.info("SqlServer container not ready yet {} {} {}",
            sqlException.getSQLState(),
            sqlException.getMessage(),
            jdbcUrl
        );
        return false;
      } catch (Exception e) {
        log.warn("connection failure {}, retrying", e.getMessage());
        return false;
      }
    };
  }

  @Override
  public Function<CreateContainerCmd, CreateContainerCmd> modifyCreateCommand() {
    // return createCmd unmodified by default
    return createCmd -> {
      List<String> envList = new ArrayList<>();

      if (createCmd.getEnv() != null && createCmd.getEnv().length > 0) {
        envList.addAll(Arrays.asList(createCmd.getEnv()));
      }
      envList.add("SA_PASSWORD=" + getPassword());
      envList.add("ACCEPT_EULA=Y");

      return createCmd.withEnv(envList);
    };
  }

  public String getPassword() {
    return "Str0ng#Passw#rd123";
  }

  public String getUsername() {
    return "SA";
  }

  public String getJdbcUrl(ContainerRuntimeSpec container) {
    return getJdbcDefaultPort(container);
  }

  private static String getJdbcDefaultPort(ContainerRuntimeSpec container) {
    return String.format("jdbc:sqlserver://%s:%d",
        container.getContainerIpAddress(),
        container.getRequiredMappedPort(MS_SQL_SERVER_PORT));
  }

}
