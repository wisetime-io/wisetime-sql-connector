# WiseTime SQL Connector

The WiseTime SQL Connector connects [WiseTime](https://wisetime.io) to SQL databases and will upsert a new WiseTime tag whenever a new entity is discovered using the configured SQL.

In order to use the WiseTime Inprotech Connector, you will need a [WiseTime Connect](https://wisetime.io/docs/connect/) API key. The WiseTime Inprotech Connector runs as a Docker container and is easy to set up and operate.

## Database Permissions Requirements

The database user that the connector uses (see below JDBC_USER variable) requires read access to the datables that the SQL attempts to read from.

## Configuration

Configuration is done through environment variables. The following configuration options are required.

| Environment Variable | Description |
| --- | --- |
| API_KEY | Your WiseTime Connect API Key |
| TAG_UPSERT_PATH | The tag folder path to use for tags |
| TAG_SQL | The SQL to run to detect new tags to be upserted to WiseTime. See below for SQL requirements. |
| JDBC_URL | The JDBC URL for your database |
| JDBC_USER | Username to use to connect to the database |
| JDBC_PASSWORD | Password to use to connect to the database |

### `TAG_SQL` Requirements

The `TAG_SQL` needs to select the relevant information as `tag_name`, `keyword`, `description` and `sync_marker`. The connector will upsert a tag into WiseTime for each row in the result set. The connector uses `sync_marker` to remember what tags it has already upserted. Here's a sample `TAG_SQL`:

```sql
SELECT TOP 500
[IRN] AS [tag_name],
[IRN] AS [keyword],
[TITLE] AS [description],
[DATE_UPDATED] AS [sync_marker]
FROM [dbo].[CASES]
WHERE [DATE_UPDATED] >= :previous_sync_marker
ORDER BY [DATE_UPDATED] ASC;
```

Because the `DATE_UPDATED` column is a DateTime field, the where clause comparison operator is `>=`. The connector will take care of deduplication before upserting tags to WiseTime. In any case, upserting a tag is an idempotent operation. If the `sync_marker` field is an auto incremented integer field, then we can simply use `>` as the comparison operator.

The WHERE clause must contain a `:previous_sync_marker` parameter placeholder. The connector will inject its current sync marker value at this position in the SQL query.

### Optional Configuration Parameters

The following configuration parameters are optional.

| Environment Variable | Description |
| ---  | --- |
| DATA_DIR | If set, the connector will use the directory as the location for storing data to keep track on the cases and projects it has synced. By default, WiseTime SQL Connector will create a temporary dir under /tmp as its data storage. |
| LOG_LEVEL | Define log level. Available values are: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` and `OFF`. Default is `INFO`. |

## Running the WiseTime SQL Connector

The easiest way to run the SQL Connector is using Docker. For example:

```text
docker run -d \
    --restart=unless-stopped \
    -e API_KEY=yourwisetimeapikey \
    -e TAG_UPSERT_PATH=/My Connected System/ \
    -e TAG_SQL="SELECT TOP 500 [IRN] AS [tag_name], [IRN] AS [keyword], [TITLE] AS [description], [DATE_UPDATED] AS [sync_marker] FROM [dbo].[CASES] WHERE [DATE_UPDATED] >= :sync_marker ORDER BY [DATE_UPDATED] ASC;"
    -e JDBC_URL="jdbc:sqlserver://HOST:PORT;databaseName=DATABASE_NAME;ssl=request;useCursors=true" \
    -e JDBC_USER=dbuser \
    -e JDBC_PASSWORD=dbpass \
    wisetime/wisetime-sql-connector
```

The SQL connector runs self-checks to determine whether it is healthy. If health check fails, the connector will shutdown. This gives us a chance to automatically re-initialise the application through the Docker restart policy.

## Logging

The connector uses [logback](https://logback.qos.ch) as logging framework. Default log level is `INFO`, you can change it by setting `LOG_LEVEL` configuration.

To setup own appenders or add another customization you can add `logback-extra.xml` on classpath. For more information see [File inclusion](https://logback.qos.ch/manual/configuration.html#fileInclusion).

### Logging to AWS CloudWatch

If configured, the Inprotech Connector can send application logs to [AWS CloudWatch](https://aws.amazon.com/cloudwatch/). In order to do so, you must supply the following configuration through the following environment variables.

| Environment Variable  | Description                                          |
| --------------------- | ---------------------------------------------------- |
| AWS_ACCESS_KEY_ID     | AWS access key for account with access to CloudWatch |
| AWS_SECRET_ACCESS_KEY | Secret for the AWS access key                        |
| AWS_REGION            | AWS region to log to                                 |

## Building

To build a Docker image of the WiseTime SQL Connector, run:

```text
make docker
```
