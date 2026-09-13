# transittrack-explorer

## Monitoring

Prometheus scrapes the backend at [http://localhost:8088/actuator/prometheus](http://localhost:8088/actuator/prometheus); its local UI is [http://localhost:9090](http://localhost:9090). Grafana is provisioned at [http://localhost:4000](http://localhost:4000) (local credentials: `admin` / `admin`) with overview, GTFS import, AVL/matching, and prediction dashboards. Alert rules currently remain in the Grafana UI; no external notification channel is configured.

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

## Derived schedule model

After each GTFS revision is validated, the `eu.transittrack.schedule` subsystem
derives trip patterns, stop paths (with geometry), blocks, and per-trip
schedule times — the foundation for AVL-based arrival/departure prediction.

See **[docs/schedule.md](docs/schedule.md)**. Design spec:
[docs/superpowers/specs/2026-08-30-derived-schedule-model-design.md](docs/superpowers/specs/2026-08-30-derived-schedule-model-design.md).

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
