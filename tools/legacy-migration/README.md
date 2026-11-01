# Migrating from TheTransitClock (Transitime)

`legacy-migrate.sh` moves the parts of a TheTransitClock database that still matter into lens:

1. **`export-gtfs`** rebuilds a GTFS zip from a legacy config revision. lens then ingests it through its
   normal pipeline, which validates it and derives patterns, stop paths, blocks, and travel times.
2. **`seed-learning`** replays the legacy `arrivals_departures` history into lens's learned travel times
   (`travel_time_observation` for the historical average and `kalman_travel_time_state` for Kalman). With
   this, predictions start from the legacy system's experience instead of from the timetable.

The script only reads the legacy database. On the lens side it writes nothing except the learned
travel-time rows and a temporary `legacy_migration` schema, which it drops when it finishes.

## What is migrated

| Legacy table(s) | Fate | Why |
| --- | --- | --- |
| `agencies`, `routes`, `stops`, `calendars`, `calendar_dates`, `trips`, `frequencies`, `transfers`, `fare_attributes`, `fare_rules` | → GTFS zip | lens ingests GTFS. It never takes raw schedule rows. |
| `trip_patterns`, `trip_pattern_to_path`, `stop_paths`, `trip_scheduled_times_list` | → `stop_times.txt` | TheTransitClock doesn't keep `stop_times`, so they are rebuilt from each trip's pattern and schedule list. |
| `stoppath_locations` | → `shapes.txt` | Each shape is rebuilt from the stop-path geometry of its longest pattern. |
| `arrivals_departures` | → learned travel times | This is the durable value: stop-to-stop running times, bucketed by day type and time band. |
| `blocks`, `block_to_trip`, `travel_times_for_*`, `active_revisions`, `config_revisions` | re-derived | lens derives these itself from the GTFS. |
| `avl_reports`, `matches`, `vehicle_states`, `predictions`, `prediction_accuracy`, `prediction_events`, `vehicle_events`, `headways`, `holding_times`, `stop_path_predictions`, `measured_arrival_times`, `monitoring_events` | not migrated | lens keeps raw history only for hours or days (AVL 24 h, matches 72 h, accuracy 30 days). Accuracy also isn't comparable across algorithms. |
| `vehicle_configs`, `vehicle_to_block_configs`, `api_keys`, `web_agencies`, `export_table`, `flyway_schema_history`, `test` | not migrated | lens has no equivalent. Vehicles appear from AVL, and assignments come from the AVL feed (`assignment-mode`). |

## Prerequisites

- `bash`, `zip`, and `psql`. psql can also run inside a container, e.g.
  `PSQL="docker exec -i my-postgres psql"`. SQL is always fed on stdin.
- `LEGACY_DB_URL` and `TARGET_DB_URL` as libpq URLs, e.g. `postgresql://postgres:password@localhost:5432/explorer`.
  With `PSQL=docker exec …`, these URLs are resolved from inside that container.

### Restoring the legacy dump

The dump is a directory-format `pg_dump` with a `restore.sql` that loads `$$PATH$$/NNNN.dat` files.
Restore it into its own database, never the lens one:

```sh
cd /path/to/extracted/dump
sed "s|\$\$PATH\$\$|$PWD|g" restore.sql > restore.local.sql
# restore.sql drops and creates the database "KOMB" itself; remove those lines to restore elsewhere.
psql -U postgres -f restore.local.sql
```

If you have the archive (`.tar` or `-Fd` directory), `pg_restore -d legacy_db dump` also works.

## Procedure

### 1. Export the GTFS

```sh
export LEGACY_DB_URL=postgresql://postgres:password@localhost:5432/KOMB
tools/legacy-migration/legacy-migrate.sh export-gtfs --out legacy.zip            # active config revision
tools/legacy-migration/legacy-migrate.sh export-gtfs --out legacy.zip --config-rev 42
```

**Skip this step if the agency still publishes its GTFS.** In that case, point lens at the live URL. The
learned travel times attach to any feed whose patterns have the same route and stop sequence.

### 2. Ingest it into lens

lens downloads feeds over HTTP, so serve the zip, e.g. `python3 -m http.server 8765` in its directory.
Then either add a config feed (see [docs/gtfs.md](../../docs/gtfs.md)):

```yaml
transittrack:
  feed:
    feeds:
      - code: komb
        name: KOMB
        url: http://my-host:8765/legacy.zip
        auto-activate: true
```

or register one at runtime through GraphQL (`/graphql`):

```graphql
mutation { registerFeed(input: {code: "komb", name: "KOMB", url: "http://my-host:8765/legacy.zip", autoActivate: true}) { code } }
mutation { ingestFeed(feedCode: "komb") { id status } }
```

Wait until the revision is `ACTIVE`, or `READY` and then activated with `activateRevision`.

### 3. Seed the learned travel times

```sh
export LEGACY_DB_URL=postgresql://postgres:password@legacy-host:5432/KOMB
export TARGET_DB_URL=postgresql://postgres:password@lens-host:5432/explorer
tools/legacy-migration/legacy-migrate.sh seed-learning --feed-code komb
```

| Option | Meaning |
| --- | --- |
| `--since TS`, `--until TS` | Replay only `arrivals_departures` in `[since, until)`, in legacy local time. Recent months reflect today's traffic best. |
| `--legacy-tz ZONE` | The zone the legacy naive timestamps are in. Defaults to the legacy agency timezone. |
| `--replace` | Overwrite learned rows that already exist. By default, rows lens has already learned are kept. |
| `--keep-staging` | Keep the `legacy_migration` schema for inspection. `cleanup` drops it later. |

It prints how many legacy patterns matched, how many stop passes and samples were used, and the routes
whose history couldn't be matched.

**Run it before lens starts learning for the feed, or with AVL disabled for it.** The rows are written in
one transaction, but lens also updates them from live vehicles. With `--replace`, the replay overwrites
whatever lens learned in the meantime.

## How the replay works

`seed-learning` reproduces `PredictionService.recordStopPasses` and the learners in
`eu.transittrack.predict.learn` sample by sample, in time order:

- **Stop passes.** A pass is leaving stop 0 (its departure) or reaching stop *k* ≥ 1 (its arrival). Only
  the first pass per stop per run counts.
- **Samples.** A sample is `pass(k) − pass(k−1)` within one run, i.e. one vehicle on one trip on one
  service day. It must be within `0 < s ≤ MAX_PLAUSIBLE_SEC`.
- **Buckets.** Each sample updates three buckets: its (day type, time band), its day type for all bands,
  and the overall bucket. The bucket comes from the service day and from the time the vehicle left stop
  *k−1*, in the agency timezone of lens's active revision.
- **Learners.**
  - The running average and the Kalman filter use the same window, prior, outlier clip, noise, and
    variance growth over time as lens.
  - The learners are seeded with lens's scheduled stop-path travel time plus the preceding dwell.
  - Tune them with the environment variables listed in `legacy-migrate.sh --help`. Match any overrides
    you set under `transittrack.predict.learn`.
- **Pattern matching.** A legacy trip pattern matches each lens pattern of the same feed with the same
  `route_id` and ordered stop ids. The shape is ignored. Learned rows are keyed by lens's `pattern_key`,
  so history on patterns whose route or stops changed is skipped and reported.

## Caveats

- **Shapes** are approximations. They are stop-path geometry, which TheTransitClock may have simplified.
- **Fields lost.** `stop_times.txt` has no `pickup_type`/`drop_off_type`, `timepoint`,
  `stop_headsign`, or `shape_dist_traveled`, because TheTransitClock doesn't store them. Stops without a
  scheduled time are exported untimed, and lens interpolates them.
- **Frequency trips.** Their service day is guessed: runs starting before 03:00 count on the previous
  day.
- **Retention.** lens deletes learned rows whose `pattern_key` no longer exists in any stored revision of
  the feed. Rows on patterns that a later GTFS changes are lost, exactly as with natively learned rows.
