# How to run locally

## Start a Postgres docker container

Use the latest Postgres image: https://hub.docker.com/_/postgres

```
docker run --name connector-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d postgres
```

Once the Postgres container is running, create a new data source in Intellij IDEA connecting to the Postgres instance, and initialise the database using the test SQL: `test/resources/db_schema/postgres/V1__test_postgres_data.sql`

## Start `wisetime-sql-connector` container

Three files are prepared in the same folder of this document.

* docker-compose.yaml
* tag_sql.yaml
* activity_types_sql.yaml

The API key can be obtained by manually creating a connector via your test team's settings page.

Now, we can run the `wisetime-sql-connector` using the following command:

```shell
docker-compose -p wisetime-sql-connector up -d
```

Watch logs from Docker Desktop.
