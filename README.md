# WiseTime SQL Connector

The WiseTime SQL Connector connects [WiseTime](https://wisetime.io) to SQL databases and will upsert a new WiseTime tag whenever a new entity is discovered using the configured SQL.

In order to use the WiseTime Inprotech Connector, you will need a [WiseTime Connect](https://wisetime.io/docs/connect/) API key. The WiseTime Inprotech Connector runs as a Docker container and is easy to set up and operate.

## Database Permissions Requirements

The database user that the connector uses (see below JDBC_USER variable) requires read access to the tables that the SQL attempts to read from.

## Configuration

Configuration is done through environment variables. The following configuration options are required.

| Environment Variable | Description |
--- | ---
| API_KEY | Your WiseTime Connect API Key |
| TAG_UPSERT_PATH | The tag folder path to use for tags |
| TAG_SQL_FILE | The path to a YAML configuration file containing the SQL queries to run to detect new tags and keywords to be upserted to WiseTime. The connector will watch the file for updates and is able to switch to the new queries as the file is updated, without restarting the connector. See below for file format. |
| JDBC_URL | The JDBC URL for your database |
| JDBC_USER | Username to use to connect to the database |
| JDBC_PASSWORD | Password to use to connect to the database |

### `TAG_SQL_FILE` Requirements

The yaml configuration file expects one or more SQL queries to be provided, each labelled with a unique name. Here's a sample `TAG_SQL_FILE`:

```yaml
name: cases
initialSyncMarker: 2001-01-01T00:00:00
skippedIds:
  - P01003789
  - P1025477
sql: >
  SELECT TOP 100
  [IRN] as [id],
  [IRN] AS [tag_name],
  [IRN] AS [additional_keyword],
  [TITLE] AS [tag_description],
  [PROPS] AS [tag_metadata],
  [DATE_UPDATED] AS [sync_marker]
  FROM [dbo].[CASES]
  WHERE [DATE_UPDATED] >= :previous_sync_marker
  AND [IRN] NOT IN (:skipped_ids)
  ORDER BY [DATE_UPDATED] ASC;

---
name: keywords
initialSyncMarker: 0
skippedIds: 0
sql: >
  SELECT TOP 100
  [PRJ_ID] AS [id],
  [IRN] AS [tag_name],
  CONCAT('FID', [PRJ_ID]) AS [additional_keyword],
  [DESCRIPTION] AS [tag_description],
  [META] AS [tag_metadata],
  [PRJ_ID] AS [sync_marker]
  FROM [dbo].[PROJECTS]
  WHERE [PRJ_ID] >= :previous_sync_marker
  AND [PRJ_ID] NOT IN (:skipped_ids)
  ORDER BY [PRJ_ID] ASC;
continuousResync: no
```

In the above example, we have provided two queries, named `cases` and `keywords`. The connector will run the queries and upsert a tag in WiseTime for each record found. Starting from the top of the configuration file, each query is run repeatedly until there are no more records before moving on to the next query. This is behaviour is especially desirable when doing initial imports of a large number of tags. In this example we would prefer to import all cases first before moving on to the secondary task of detecting keywords.

Selecting an empty string for a field lets the connector know that we don't want to overwrite the field during upsert if a tag already exists. For example, when sending keywords to WiseTime via the second query, we don't want to change the current tag description.

The `initialSyncMarker` configuration is required and specifies the first value to be used for `:previous_sync_marker`.

The default sync behaviour of the of the connector is to detect all unsynced tags and sync them as fast as possible with WiseTime until there are no more tags detected by a query. In addition to this fast sync, a slow continuous sync can be configured for each query. The slow sync will sync one batch every 5 minutes. The `continuousResync` configuration enables or disables a slow resync that runs continuously and resets the sync marker when no more records are found. I.e. the connector resyncs from the start as it reaches the end.

#### Selected Fields

The `TAG_SQL` must select the relevant information as `id`, `tag_name`, `additional_keyword`, `tag_description` and `sync_marker`. The connector expects these names in the result set. The connector uses `sync_marker` to remember what tags it has already upserted. The following table explains how each selected field is used by the connector.

| Field | Explanation |
--- | ---
| id | Used to perform deduplication during sync when the sync_marker is not unique |
| tag_name | Used as the tag name when creating a tag for the record |
| additional_keyword | Used as the keyword to **add** to the tag during upsert. Previous keywords are not removed if the tag already exists. |
| tag_description | Used as the tag description when creating the tag. This description will be searchable in the WiseTime Console UI. If empty, will not overwrite an existing description when upserting tag. |
| sync_marker | Used as the sync position marker so that the connector remembers which records it has already synced. Should be comparable. |
| tag_metadata |  Used as the tag metadata. The metadata represents a map of key-value pairs that will be recorded against the tag, eg. [\{"url":"http://test.instance/P12012123GBT1"},\{"tag type 1":"Patent"},\{"tag type 2":"Great Britain"},\{"tag type 3":"Divisional"}].  |

#### Placeholder Parameters

The SQL queries are parametised and the following placeholder parameters are required. They must be provided verbatim in each query.

| Placeholder Parameter | Explanation |
--- | ---
| :previous_sync_marker | The connector will inject its current sync marker value at this position in the SQL query. This is how the connector skips over records that it has already synced. |
| :skipped_ids | The connector will inject a list of IDs to skip plus previously synced IDs as a CSV using this placeholder parameter. This is how the connector performs deduplication in the event that the sync marker is non-unique, e.g. when a datetime type is used as the sync marker. |

For `cases` in the above example, because the `DATE_UPDATED` column is a DateTime field, we use the comparison operator `>=` in the WHERE clause. The connector will take care of deduplication before upserting tags to WiseTime. In any case, upserting a tag is an idempotent operation.

For the `keywords` query in the above example, if the `sync_marker` field is an auto incremented integer field, then we can simply use `>` as the comparison operator. In this case, the clause `AND [PRJ_ID] NOT IN (:skipped_ids)` is redundant. However, we must still use the placeholder because the connector expects it when it generates the query.

#### Tag SQL Configuration Hot Reloading

The connector will detect changes to the tag SQL configuration file and automatically use the updated configuration. Sync state is reset and sync restarted if any of the following fields have changed for a configured query:

* initialSyncMarker
* skippedIds
* sql
* continuousResync

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
    -e TAG_SQL_FILE=/connector/tag_sql.yaml \
    -e JDBC_URL="jdbc:sqlserver://HOST:PORT;databaseName=DATABASE_NAME;ssl=request;useCursors=true" \
    -e JDBC_USER=dbuser \
    -e JDBC_PASSWORD=dbpass \
    wisetime/wisetime-sql-connector
```

The SQL connector runs self-checks to determine whether it is healthy. If health check fails, the connector will shutdown. This gives us a chance to automatically re-initialise the application through the Docker restart policy.

## Building

To build a Docker image of the WiseTime SQL Connector, run:

```text
make docker
```
