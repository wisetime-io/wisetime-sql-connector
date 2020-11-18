# WiseTime SQL Connector

The WiseTime SQL Connector connects [WiseTime](https://wisetime.io) to SQL databases and will upsert a new WiseTime tag whenever a new entity is discovered using the configured SQL.

The WiseTime SQL Connector can also sync activity types to WiseTime if configured.

In order to use the WiseTime SQL Connector, you will need a [WiseTime Connect](https://wisetime.io/docs/connect/) API key. The WiseTime SQL Connector runs as a Docker container and is easy to set up and operate.

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
  [URL] AS [url],
  [EBILLREF] AS [external_id],
  [IRN] AS [additional_keyword],
  [TITLE] AS [tag_description],
  (SELECT
                 [CLIENT_NAME] as Client,
                 [PROJECT_NAME] as Project
               FROM [dbo].[PROJECTS] 
               WHERE [dbo].[PROJECTS].[IRN] = [dbo].[CASES].[IRN]
               FOR JSON PATH, WITHOUT_ARRAY_WRAPPER  
  ) AS [tag_metadata],   
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
  [URL] AS [url],
  [EBILLREF] AS [external_id],
  [DESCRIPTION] AS [tag_description],
  (SELECT 
     [CLIENT_NAME] as Client,
     [PROJECT_NAME] as Project
      FROM [dbo].[META_STORE] 
      WHERE [dbo].[META_STORE].[IRN] = [dbo].[PROJECTS].[IRN]
      FOR JSON PATH, WITHOUT_ARRAY_WRAPPER    
  ) AS [tag_metadata], 
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
| url | Not Required. If present will be used by Wisetime frontend  as a link to the external system |
| external_id | Not required. id of the activity in the external system |
| additional_keyword | Used as the keyword to **add** to the tag during upsert. Previous keywords are not removed if the tag already exists. |
| tag_description | Used as the tag description when creating the tag. This description will be searchable in the WiseTime Console UI. If empty, will not overwrite an existing description when upserting tag. |
| sync_marker | Used as the sync position marker so that the connector remembers which records it has already synced. Should be comparable. |
| tag_metadata | Not Required. Used as the tag metadata. The metadata represents a map of key-value pairs that will be recorded against the tag, eg. {"url":"http://test.instance/P12012123GBT1", "tag type 1":"Patent", "tag type 2":"Great Britain", "tag type 3":"Divisional"}.  |

#### Tag metadata

The metadata represents a map of key-value pairs that will be recorded against the tag. 
This is essentially a JSON object. In the examples above it is built using a select
statement with FOR JSON PATH, WITHOUT_ARRAY_WRAPPER in the end.
To understand how it works lets have a look at some more detailed examples.

Let's assume that we have two tables: PROJECT and CASES.
 
**PROJECTS** 

| IRN | CLIENT_NAME | PROJECT_NAME | PROJECT_DESCRIPTION | URL |
--- | --- | --- | ---- | ----
| P1000 | CLIENT 1 | PROJECT 1 | Project description 1 | http://www.google.com 
| P2000 | CLIENT 2 | PROJECT 2 | Project description 2 | http://www.google.com
| P3000 | CLIENT 3 | PROJECT 3 | Project description 3 | http://www.google.com
| P4000 | CLIENT 4 | PROJECT 4 | Project description 4 | http://www.google.com
| P5000 | CLIENT 5 | PROJECT 5 | Project description 5 | http://www.google.com

**CASES**

| IRN | TITLE | DESCRIPTION | DATE_UPDATED |
--- | --- | --- | ---
| P1000 | CASE 1 | DESCRIPTION 1 | 01.01.2020
| P2000 | CASE 2 | DESCRIPTION 2 | 02.01.2020  
| P3000 | CASE 3 | DESCRIPTION 3 | 03.01.2020
| P4000 | CASE 4 | DESCRIPTION 4 | 04.01.2020
| P5000 | CASE 5 | DESCRIPTION 5 | 05.01.2020


```sql
SELECT
     [CLIENT_NAME] as Client,
     [PROJECT_NAME] as Project,
     [PROJECT_DESCRIPTION] as Description
    FROM [PROJECTS] 
    FOR JSON PATH, WITHOUT_ARRAY_WRAPPER     
```
This select will return a set of json string objects
```json
{ "Client": "CLIENT 1", "Project":"PROJECT 1", "Description":"Project description 1"},
{ "Client": "CLIENT 2","Project":"PROJECT 2",  "Description":"Project description 2"},
{ "Client": "CLIENT 3", "Project":"PROJECT 3", "Description":"Project description 3"},
{ "Client": "CLIENT 4", "Project":"PROJECT 4", "Description":"Project description 4"},
{ "Client": "CLIENT 5", "Project":"PROJECT 5", "Description":"Project description 5"},
```
These objects will be transformed into three metadata tags
Client, Project, Description. But we have 5 records in database. It means that 4
records will be overridden by the record retrieved last.
To avoid such a situation we should clearly indicate which record should be used.

For example  
```sql
SELECT TOP 100
  [IRN] as [id],
  [IRN] AS [tag_name],
  [EBILLREF] AS [external_id],
  [IRN] AS [additional_keyword],
  [TITLE] AS [tag_description],
  (SELECT
      [CLIENT_NAME] as Client,
      [PROJECT_NAME] as Project
      FROM [dbo].[PROJECTS] 
      WHERE [dbo].[PROJECTS].[IRN] = [dbo].[CASES].[IRN]
      FOR JSON PATH, WITHOUT_ARRAY_WRAPPER  
  ) AS [tag_metadata],   
  [DATE_UPDATED] AS [sync_marker]
  FROM [dbo].[CASES]
  ORDER BY [DATE_UPDATED] ASC;
```
We will get the following set of values 


| id | tag_name | external_id | additional_keyword | tag_description |tag_metadata | date_updated |
--- | --- | --- | ---- | --- | --- | ---
| P1000 | P1000 | E1000 | P1000 | CASE 1 | { "Client": "CLIENT 1", "Project":"PROJECT 1"}| 01.01.2020
| P2000 | P2000 | E2000 | P1000 | CASE 2 | { "Client": "CLIENT 2", "Project":"PROJECT 2"}| 02.01.2020
| P3000 | P3000 | E3000 | P1000 | CASE 3 | { "Client": "CLIENT 3", "Project":"PROJECT 3"}| 03.01.2020
| P4000 | P4000 | E4000 | P1000 | CASE 4 | { "Client": "CLIENT 4", "Project":"PROJECT 4"}| 04.01.2020
| P5000 | P5000 | E5000 | P1000 | CASE 5 | { "Client": "CLIENT 5", "Project":"PROJECT 5"}| 05.01.2020


The similar approach can be used for Postgres database. The query below will return the exactly same result.
```sql
SELECT 
  IRN as id,
  IRN AS tag_name,
  IRN AS external_id,
  URL AS url,
  IRN AS additional_keyword,
  TITLE AS tag_description,
  (select row_to_json(t)
      from (
              SELECT
                CLIENT_NAME as Client,
                PROJECT_NAME as Project
             FROM dbo.PROJECTS 
             WHERE dbo.PROJECTS.IRN = dbo.CASES.IRN             
           ) t 
  ) AS tag_metadata,   
  DATE_UPDATED AS sync_marker
  FROM dbo.CASES
  ORDER BY DATE_UPDATED ASC LIMIT 100;
```

The metadata can also be retrieved from multiple tables.
Let's assume that we have another table called TASKS that also
contains some tag metadata.

| IRN | TEAM_NAME | TASK_NAME | TASK_DESCRIPTION | DATE
--- | --- | --- | ---- | ---
| P1000 | TEAM 1 | TASK 1 | DESCRIPTION 1 | 01.01.2020
| P2000 | TEAM 2 | TASK 2 | DESCRIPTION 2 | 02.01.2020
| P3000 | TEAM 3 | TASK 3 | DESCRIPTION 3 | 03.01.2020
| P4000 | TEAM 4 | TASK 4 | DESCRIPTION 4 | 04.01.2020
| P5000 | TEAM 5 | TASK 5 | DESCRIPTION 5 | 05.01.2020 
 
Here is an example how we can build tag metadata from multiple tables using INNER JOIN

```sql
SELECT TOP 100
  [IRN] as [id],
  [IRN] AS [tag_name],
  [EBILLREF] AS [external_id],
  [URL] AS [url],
  [IRN] AS [additional_keyword],
  [TITLE] AS [tag_description],
  (SELECT
     [p].[CLIENT_NAME] as Client,
     [p].[PROJECT_NAME] as Project,
     [t].[TEAM_NAME] as Team,
     [t].[TASK_NAME]  as Task
     FROM [dbo].[PROJECTS] p
     INNER JOIN [dbo].[TASKS] t 
     ON [dbo].[PROJECTS].[IRN]= [dbo].[TASKS].[IRN]
     WHERE [dbo].[PROJECTS].[IRN] = [dbo].[CASES].[IRN]
     FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
  ) AS [tag_metadata],   
  [DATE_UPDATED] AS [sync_marker]
  FROM [dbo].[CASES]
  ORDER BY [DATE_UPDATED] ASC;

```

This query will produce following results 

| id | tag_name | external_id | url | additional_keyword | tag_description |tag_metadata | date_updated |
--- | --- | --- | ---- | --- | --- | --- | ---
| P1000 | P1000 | E1000 | http://www.google.com | P1000 | CASE 1 | { "Client": "CLIENT 1", "Project":"PROJECT 1","Team":"TEAM 1","Task":"TASK 1"}| 01.01.2020
| P2000 | P2000 | E2000 | http://www.google.com | P1000 | CASE 2 | { "Client": "CLIENT 2", "Project":"PROJECT 2","Team":"TEAM 2","Task":"TASK 2"}| 02.01.2020
| P3000 | P3000 | E3000 | http://www.google.com | P1000 | CASE 3 | { "Client": "CLIENT 3", "Project":"PROJECT 3","Team":"TEAM 3","Task":"TASK 3"}| 03.01.2020
| P4000 | P4000 | E4000 | http://www.google.com | P1000 |CASE 4 | { "Client": "CLIENT 4", "Project":"PROJECT 4","Team":"TEAM 4","Task":"TASK 4"}| 04.01.2020
| P5000 | P5000 | E5000 | http://www.google.com | P1000 |CASE 5 | { "Client": "CLIENT 5", "Project":"PROJECT 5","Team":"TEAM 5","Task":"TASK 5"}| 05.01.2020


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
| ACTIVITY_TYPE_SQL_FILE | The path to a YAML configuration file containing the SQL queries to run to fetch all activity types to be propagated to WiseTime. The connector will watch the file for updates and is able to switch to the new queries as the file is updated, without restarting the connector. See below for file format. |
| DATA_DIR | If set, the connector will use the directory as the location for storing data to keep track on the cases and projects it has synced. By default, WiseTime SQL Connector will create a temporary dir under /tmp as its data storage. |
| LOG_LEVEL | Define log level. Available values are: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` and `OFF`. Default is `INFO`. |

### `ACTIVITY_TYPE_SQL_FILE` Requirements

The yaml configuration file expects single SQL query to be provided. Here's a sample `ACTIVITY_TYPE_SQL_FILE`:

```yaml
initialSyncMarker: 0
skippedCodes:
  - 23456
sql: >
  SELECT TOP 100
  [ACTIVITYCODE] AS [code],
  [ACTIVITYCODE] AS [sync_marker],
  [ACTIVITYNAME] AS [label]
  [ACTIVITYDESCRIPTION] AS [description]
  FROM [dbo].[TEST_ACTIVITYCODES]
  WHERE [ACTIVITYCODE] NOT IN (:skipped_codes) AND [ACTIVITYCODE] > :previous_sync_marker
  ORDER BY [sync_marker];
```

In the above example, we have provided a query that the connector will use for both regular and slow loop sync.

If `sync_marker` is used the query must be sorted by it (e.g. `ORDER BY [sync_marker]`). A column that is used as `sync_marker` must be not updatable.

The `initialSyncMarker` configuration is required and specifies the first value to be used for `:previous_sync_marker`.

Selecting an empty list will disable all the existing WiseTime activity types.

`skippedCodes` is an optional property. You can omit it if there are no activity types you would like to avoid syncing. You must avoid using `skipped_codes` in your SQL in this case.

If there's no column you can use as `sync_marker` you should avoid it. You should also avoid using `TOP`/`LIMIT` in your query so connector will fetch all the activity types on each run and sync them if any has been changed/created/deleted since previous sync.

It's highly recommended to use `ORDER BY` in your query to reduce the potential load on WiseTime as we use a caching mechanism that is order-dependent
 
Here's an example:
```yaml
sql: >
  SELECT
  [ACTIVITYCODE] AS [code],
  [ACTIVITYNAME] AS [label]
  [ACTIVITYDESCRIPTION] AS [description]
  FROM [dbo].[TEST_ACTIVITYCODES];
  ORDER BY [code];
```

#### Selected Fields

The `ACTIVITY_TYPE_SQL_FILE` must select the relevant information as `code`, `label` and `description` of the activity type.

| Field | Explanation |
--- | ---
| code | Will be used as activity type code during time posting |
| label | Used as the WiseTime console label of the created activity type |
| description | Used as the WiseTime console description of the created activity type |
| sync_marker | Used as the sync position marker so that the connector remembers which records it has already synced. Should be comparable. Should NOT be updatable if it's not a date. |

## Running the WiseTime SQL Connector

The easiest way to run the SQL Connector is using Docker. For example:

```text
docker run -d \
    --restart=unless-stopped \
    -e API_KEY=yourwisetimeapikey \
    -e TAG_UPSERT_PATH=/My Connected System/ \
    -e TAG_SQL_FILE=/connector/tag_sql.yaml \
    -e ACTIVITY_TYPE_SQL_FILE=/connector/activity_type_sql.yaml \
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
