# Derived Schedule Model

`eu.transittrack.schedule` derives a prediction-ready transit model from each
ingested GTFS revision: **trip patterns**, **stop paths** (inter-stop segments
with geometry and length), **blocks** (a vehicle's ordered work for a service
day), and per-trip **schedule times** with schedule-based travel/dwell defaults.

Design specs:
[`2026-08-30-derived-schedule-model-design.md`](superpowers/specs/2026-08-30-derived-schedule-model-design.md)
(original model) and
[`2026-09-03-schedule-derivation-processors-design.md`](superpowers/specs/2026-09-03-schedule-derivation-processors-design.md)
(current build: six `@Order`ed `IngestionPostProcessor` beans in
`schedule.derive`, travel times moved to `travel_times_for_stop_path`).
This is sub-project #1 of 3 toward AVL-based arrival/departure prediction
(the others: AVL ingestion, prediction engine).

## When it runs

Derivation is the `DERIVING` step of the GTFS ingestion pipeline, between
`VALIDATING` and `READY`. A revision only becomes `ACTIVE` if derivation
succeeded; any failure funnels to `FAILED` and every derived row for the
revision is removed. Set `transittrack.schedule.enabled: false` to skip the
step entirely.

## Model

| Table | One row per | Key fields |
| --- | --- | --- |
| `trip_pattern` | distinct `(routeId, shapeId, ordered stop-id list)` | `pattern_key`, `route_id`, `direction_id`, `shape_id`, extent bbox |
| `stop_path` | stop in a pattern (segment prev-stop -> this-stop) | `stop_path_index`, `length_m`, `path_geometry`, `wait_stop`, `layover_stop` |
| `travel_times_for_stop_path` | `(trip_pattern_id, stop_path_index)` | `travel_time_sec`, `dwell_time_sec`, `how_set` (median scheduled travel/dwell for the pattern) |
| `sched_trip` | GTFS trip | `trip_id`, `trip_pattern_id`, `block_id`, `block_seq`, `start_time_sec`, `frequency_based` |
| `schedule_time` | stop in a trip | `arrival_sec`, `departure_sec`, `interpolated`, `sched_travel_time_sec` |
| `block` | `(block_id, service_id)` | `start_time_sec`, `end_time_sec`, `route_ids` |

All times are **seconds into the service day** (may exceed 86400). All tables
are revision-scoped and cascade-delete with `gtfs_revision`.

## How patterns are built

Pattern identity is
`{routeId}|{shapeId}|{firstStop}_to_{lastStop}|{sha1(stopIds)}` — direction,
headsign and pickup/drop-off flags are stored from the first trip that
instantiates the pattern but are not part of identity. The route **is** part of
identity: `trip_pattern.route_id` is single-valued and patterns are exposed
per route, so two routes sharing a shape and stop list must not collapse onto
one pattern. Stop-path geometry is produced by projecting each stop onto the
trip's shape and slicing the polyline between consecutive projections; a stop
that projects more than `stop-projection-max-deviation-m` (default 100 m) from
the shape, or a trip with no shape, falls back to a straight line between stop
coordinates. A trip that references a stop with no resolvable `(lat, lon)` is
skipped with a warning naming the trip, route and stop — it produces no
`sched_trip` row rather than a pattern measured against `(0, 0)`.

## Schedule times

GTFS times only some stops; `ScheduleInterpolator` fills the rest by linear
interpolation on cumulative distance between the nearest timed stops, marking
them `interpolated`. `sched_travel_time_sec` / `sched_dwell_time_sec` are
denormalized per row; the median across the pattern's trips is written to a
separate `travel_times_for_stop_path` row per `(trip_pattern_id,
stop_path_index)` (`how_set = SCHEDULE`) — the schedule-based default the
prediction engine falls back to, and the seam where AVL-derived times will
later be layered in.

## Blocks and layovers

Trips sharing `(block_id, service_id)` are ordered by start time into a block;
`sched_trip.layover_after_sec` / `deadhead_after` describe the gap to the next
trip. A pattern's last `stop_path` is flagged `layover_stop` with a
`break_time_sec` when **any** trip on that pattern lays over at least
`layover-threshold-sec` (default 60 s) — this is a pattern-level heuristic,
not a per-trip guarantee.

## Frequency-based trips

Trips listed in `frequencies.txt` get a pattern, stop paths and
`schedule_time` rows expressed as **offsets from 0** (first departure).
`sched_trip.frequency_based = true` and `exact_times` is carried through; the
concrete departure times come from `gtfs_frequency` windows at read /
prediction time. Frequency trips are **not placed in blocks** even when they
carry a `block_id`, because their 0-based start times would sort to the front
of every block and corrupt the layover gaps. Full frequency handling is
deferred to the prediction sub-project.

## Read API

```
tripPatterns(feedCode, routeId, revisionId): [TripPattern!]!
tripPattern(feedCode, patternKey, revisionId): TripPattern
schedTrip(feedCode, tripId, revisionId): SchedTrip
blocks(feedCode, revisionId): [Block!]!
block(feedCode, blockId, serviceId, revisionId): Block
blocksOnDate(feedCode, date, revisionId): [Block!]!
tripsOnDate(feedCode, date, routeId, revisionId): [SchedTrip!]!
```

Nested: `TripPattern.route/stopPaths/trips`, `StopPath.stop`,
`SchedTrip.pattern/route/block/scheduleTimes`, `Block.trips` (ordered by
`block_seq`), plus `GtfsRoute.tripPatterns` and `GtfsTrip.schedTrip`.
`blocksOnDate` / `tripsOnDate` resolve active `service_id`s via
`ServiceDateResolver` (calendar windows + `calendar_dates` exceptions);
`date` is ISO-8601 and interpreted as a service day (no timezone math).

## Configuration

```yaml
transittrack:
  schedule:
    enabled: true
    layover-threshold-sec: 60
    stop-projection-max-deviation-m: 100
```
