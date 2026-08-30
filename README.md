# transittrack-explorer

A Kotlin / Spring Boot 4 service for ingesting public-transit schedule data and
serving it over GraphQL.

## GTFS ingestion

The `eu.transittrack.gtfs` subsystem downloads GTFS Schedule feeds from remote
URLs, stores the full GTFS spec in PostgreSQL, and keeps every import as an
immutable, queryable **revision**. A Spring GraphQL API serves the active
revision of each feed.

See **[docs/gtfs.md](docs/gtfs.md)** for how to register feeds (config or
mutation), the revision lifecycle, the mutations, retention/pruning, the polling
scheduler, and the read API. Design spec:
[docs/superpowers/specs/2026-08-30-gtfs-ingestion-versioned-storage-design.md](docs/superpowers/specs/2026-08-30-gtfs-ingestion-versioned-storage-design.md).

## Running tests

```bash
./gradlew test
```

The test suite uses Testcontainers, so a running Docker daemon is required.

## Running the app

```bash
docker compose up -d      # PostgreSQL + Grafana LGTM stack
./gradlew bootRun
```

GraphiQL is available at `http://localhost:8080/graphiql`.
