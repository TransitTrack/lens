# Configuration Reference

Every application-specific setting lives under the `transittrack.*` namespace
in `src/main/resources/application.yaml`, backed by `@ConfigurationProperties`
classes (`eu.transittrack.*Properties`). Standard Spring Boot mechanisms apply
throughout: environment variables (`TRANSITTRACK_AVL_ENABLED=true`), a mounted
`application.yaml`/`application-*.yaml`, or `-D`/`--` JVM args all override the
shipped defaults, and `SPRING_PROFILES_ACTIVE` layers additional
`application-<profile>.yaml` files on top (see
[docs/deployment.md](deployment.md) for the `role-*` profiles specifically).

This reference lists every property, its default, and what it controls.
Subsystem docs ([gtfs.md](gtfs.md), [schedule.md](schedule.md),
[avl.md](avl.md), [predictions.md](predictions.md)) cover the *behavior* each
setting tunes in depth; this page is the flat lookup table.

---

## 1. Feed definitions — `transittrack.feed`

The single source of truth for which transit feeds this instance tracks. Each
entry seeds a `gtfs_feed` row, and an optional nested `avl` block also seeds a
paired `avl_feed` row (see [avl.md §3](avl.md#3-avl_feed-table--avlfeedconfigsynchronizer)).

```yaml
transittrack:
  feed:
    prune-config-feeds: false   # delete CONFIG-sourced feeds no longer listed here
    feeds:
      - code: mbta                          # required, stable identifier
        name: "MBTA"                        # required, display name
        url: "https://cdn.mbta.com/gtfs.zip"
        enabled: true                       # default true
        auto-activate: true                 # default from gtfs.ingest.auto-activate
        polling-cron: "0 * * * * *"         # optional; per-feed re-ingest schedule (see gtfs.md)
        avl:                                # optional; omit for schedule-only feeds
          url: "https://cdn.mbta.com/VehiclePositions.pb"
          name: "MBTA VehiclePositions"      # default "<feed name> vehicle positions"
          format: GTFS_RT                    # GTFS_RT | STPT | ... — default GTFS_RT
          poll-interval-sec: 15              # default 15
          assignment-mode: FULL_INFERENCE    # TRUST_DESCRIPTOR | DESCRIPTOR_THEN_INFER | FULL_INFERENCE
          enabled: true                      # default true
          prediction-algorithm: KALMAN       # HISTORICAL_AVERAGE | KALMAN | SCHEDULE_ADHERENCE
          prediction-mode: SINGLE            # SINGLE | EVALUATION
          headers:                           # optional outbound request headers
            x-api-key: "…"
```

A feed can also be registered/edited at runtime through the GraphQL mutation
surface (`source = API` rows) — see [gtfs.md](gtfs.md). `prune-config-feeds`
only ever affects `source = CONFIG` rows.

---

## 2. Shared outbound HTTP client — `transittrack.http`

Used by every feed download (GTFS zips and AVL polls alike).

| Property | Default | Meaning |
| --- | --- | --- |
| `connect-timeout-ms` | `10000` | TCP connect timeout |
| `read-timeout-ms` | `60000` | Socket read timeout |
| `max-size-bytes` | `524288000` (500 MB) | Hard cap on a downloaded response body |
| `user-agent` | `transittrack/0.0.1` | `User-Agent` header sent on every request |

---

## 3. GTFS Schedule ingestion — `transittrack.gtfs`

Full behavior: [docs/gtfs.md](gtfs.md).

| Property | Default | Meaning |
| --- | --- | --- |
| `ingest.auto-activate` | `true` | Activate a revision automatically once validation succeeds |
| `ingest.strict-validation` | `false` | Fail ingestion on validator warnings, not just errors |
| `ingest.batch-size` | `1000` | JDBC batch size for bulk entity inserts |
| `ingest.temp-dir` | `/tmp` | Scratch directory for downloaded/extracted feed files |
| `retention.keep-revisions-per-feed` | `5` | Superseded revisions kept per feed before pruning |
| `polling.enabled` | `true` | Enable `GtfsIngestScheduler`'s sweep entirely |
| `polling.sweep-cron` | `0 */15 * * * *` (every 15 min) | How often the sweep checks feeds against their own `polling-cron` |
| `title-sanitizing.enabled` | `false` | Strip agency-supplied HTML/formatting from route/trip names |

`polling.sweep-cron` and a feed's own `polling-cron` are independent: the
sweep only wakes up this often to *check* whether each feed's own schedule
says it's due — see [gtfs.md](gtfs.md) for the two-cron design.

---

## 4. Derived schedule model — `transittrack.schedule`

Full behavior: [docs/schedule.md](schedule.md).

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Run schedule derivation as part of ingestion; `false` skips it entirely |
| `layover-threshold-sec` | `60` | Minimum gap between consecutive trips to count as a layover, not overlap |
| `stop-projection-max-deviation-m` | `100` | Max distance a stop may be projected off its pattern before being flagged |
| `inferred-blocks.enabled` | `true` | Infer vehicle blocks for trips with no GTFS `block_id` |
| `inferred-blocks.allow-deadhead` | `false` | Allow inferred blocks to bridge trips with a non-adjacent stop pair |
| `inferred-blocks.max-deadhead-gap-sec` | `1800` | Max time gap allowed when bridging (only used if `allow-deadhead: true`) |
| `optimize.executor.queue-capacity` | `50` | Bounded queue size for the schedule-optimization background executor |
| `optimize.materiality-sec` | `30` | Minimum schedule-time change an optimization recommendation must produce to be surfaced |
| `optimize.retention.results-retention-days` | `90` | Age-based prune of `schedule_optimization_run` rows |
| `optimize.retention.sweep-cron` | `0 30 3 * * *` (daily, 03:30) | Prune schedule (`OptimizationRetentionScheduler`) |

### Draft editing — `transittrack.draft`

| Property | Default | Meaning |
| --- | --- | --- |
| `editor-lease-minutes` | `15` | How long a draft's editor claim lasts before it can be taken over |

### Export — `transittrack.export`

| Property | Default | Meaning |
| --- | --- | --- |
| `temp-sweep-cron` | `0 */5 * * * *` (every 5 min) | Reaps abandoned export spool files |
| `temp-file-max-age-minutes` | `15` | Age threshold for a spool file to count as abandoned |

---

## 5. AVL ingestion & matching — `transittrack.avl`

Full behavior: [docs/avl.md](avl.md). **Disabled by default** in the
framework sense — but note the shipped `application.yaml` sets
`avl.enabled: true` for the bundled example feeds; set it to `false`
explicitly if you don't want any AVL beans created at all.

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch — `false` means no `AvlPoller`/`AvlMatchProcessor`/related beans exist |
| `retention.report-hours` | `24` | Age-based prune of `avl_report` (cascades `vehicle_match`) |
| `retention.match-hours` | `72` | Age-based prune of orphan-by-age `vehicle_match` rows |
| `retention.stale-vehicle-hours` | `12` | Age-based prune of `vehicle_state` by `updated_at` |
| `retention.sweep-cron` | `0 0 * * * *` (hourly) | `AvlRetentionScheduler` cadence |
| `match.backtrack-tolerance-m` | `30.0` | Allowed backward jump along the pattern before rejecting a match |
| `match.max-deviation-m` | `60.0` | Max point-to-polyline distance accepted as a match |
| `match.unmatch-after-failures` | `5` | Consecutive-miss threshold before clearing a vehicle's assignment |
| `match.trip-end-advance-grace-sec` | `120` | Grace window past a trip's scheduled end before it stops being a candidate |
| `match.candidate-time-slack-sec` | `1800` | ± window around a trip's `[start, end]` for candidacy |
| `match.match-interval-ms` | `5000` | `AvlMatchProcessor` per-feed fixed delay |
| `match.claim-batch-size` | `500` | Max `PENDING` `avl_report` rows claimed per feed per cycle |
| `match.reassign-hysteresis` | `0.15` | Score margin protecting the incumbent trip from churn (`FULL_INFERENCE`) |
| `match.score-weights.*` | `deviation 0.4, heading 0.2, schedule 0.2, continuity 0.2` | `FULL_INFERENCE` scorer weights (should sum to ~1.0) |
| `match.silent-stale-cycles` | `3` | Poll-interval multiples of silence before flagging a vehicle `stale` |
| `match.silent-unmatch-cycles` | `5` | Poll-interval multiples of silence before clearing the assignment |
| `match.silent-unmatch-min-sec` | `60` | Floor applied to both silence thresholds above |

Per-feed AVL settings (`format`, `poll-interval-sec`, `assignment-mode`, …)
are set in `transittrack.feed.feeds[].avl` — see §1.

---

## 6. Prediction generation — `transittrack.predict`

Full behavior: [docs/predictions.md](predictions.md).

| Property | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch — `false` means no `PredictionProcessor`/related beans exist |
| `run.interval-ms` | `5000` | `PredictionProcessor` per-feed fixed delay (independent of `avl.match.match-interval-ms`) |
| `run.claim-batch-size` | `500` | Max `PENDING` `vehicle_match` rows claimed per feed per cycle |
| `learn.max-plausible-travel-time-sec` | `1800` | Upper bound on a travel-time observation before it's discarded as noise |
| `learn.kalman-measurement-noise-sec2` | `400.0` | Kalman filter measurement noise variance |
| `learn.kalman-initial-variance-sec2` | `3600.0` | Kalman filter initial state variance |
| `retention.prediction-hours` | `6` | Age-based prune of `vehicle_prediction` |
| `retention.accuracy-days` | `30` | Age-based prune of `prediction_accuracy` |
| `retention.sweep-cron` | `0 15 * * * *` (hourly, minute 15) | `PredictionRetentionScheduler` cadence |

Per-feed `prediction-algorithm` / `prediction-mode` are set in
`transittrack.feed.feeds[].avl` — see §1.

---

## 7. Read cache — `transittrack.cache`

| Property | Default | Meaning |
| --- | --- | --- |
| `ttl` | `60m` | Caffeine TTL for the AVL match read cache (heap-only; see `eu.transittrack.avl.match.cache.AvlCaches`) |
| `max-entries` | `20000` | Caffeine max size |

---

## 8. Observability — `transittrack.observability`

| Property | Default | Meaning |
| --- | --- | --- |
| `gauge-refresh-ms` | `30000` | How often `TransitTrackMetricsGaugeRefresher` recomputes DB-derived queue-depth gauges outside Prometheus's own scrape thread |

Metrics are exposed at `/actuator/prometheus` (see
`management.metrics.*` in `application.yaml` for histogram/tag configuration).
See the top-level [README](../README.md#monitoring) for the bundled
Prometheus/Grafana stack.

---

## 9. Deployment roles — `SPRING_PROFILES_ACTIVE`

Not a `transittrack.*` property — this is a plain Spring profile. Setting one
or more `role-*` profiles restricts which background components run in this
process; setting none runs everything (monolith mode, the default for local
dev). Full detail, including per-role scaling guidance and the ShedLock
coordination this relies on: **[docs/deployment.md](deployment.md#3-roles)**.

| Profile | Restricts to |
| --- | --- |
| `role-api` | GraphQL/web layer only — no background schedulers (they're always loaded, but this is the role meant to receive traffic) |
| `role-ingester` | `GtfsIngestScheduler` |
| `role-feed-processor` | `AvlPoller`, `AvlMatchProcessor`, plus all cluster-singleton housekeeping jobs |
| `role-predictor` | `PredictionProcessor` |

---

## 10. Infrastructure — Spring/JPA/Liquibase/logging

Standard Spring Boot properties, listed here because they're commonly
overridden per environment:

| Property | Default | Meaning |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:postgresql://${POSTGRES_PORT_5432_TCP_ADDR:localhost}:${POSTGRES_PORT_5432_TCP_PORT:5432}/explorer` | JDBC URL — override directly, or via the two `POSTGRES_PORT_5432_TCP_*` env vars (Docker-links style) |
| `spring.datasource.username` | `postgres` | DB username |
| `spring.datasource.password` | `${POSTGRES_PASSWORD:password}` | DB password — set via `POSTGRES_PASSWORD` env var |
| `spring.liquibase.enabled` | `true` | Run migrations on startup |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Never auto-generates schema — Liquibase owns it; this only validates the mapping matches |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Which actuator endpoints are reachable over HTTP |
| `logging.level.eu.transittrack` | `debug` | Application log level — turn down for production (`info` or `warn`) |
| `logging.level.root` | `warn` | Framework/library log level |

---

## 11. ShedLock — the `shedlock` table

Not application-configurable — it's infrastructure the app manages itself.
`SchedulerLockConfig` wires a `JdbcTemplateLockProvider` against the same
`DataSource`, backed by the `shedlock` table (migration
`0009-shedlock.yaml`). Every cluster-singleton scheduled job carries its own
`@SchedulerLock(lockAtMostFor, lockAtLeastFor)`, and the per-feed processors
(`AvlPoller`, `AvlMatchProcessor`, `PredictionProcessor`) coordinate through
`FeedLockCoordinator` on top of the same provider. See
[docs/deployment.md](deployment.md#4-coordination) for what this buys you when
running more than one replica of a role.
