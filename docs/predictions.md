# Prediction generation

This subsystem (`eu.transittrack.predict`) computes per-stop arrival/departure
**predictions** for every AVL-matched vehicle, tracks their accuracy against
what actually happened, and exposes both — plus a derived route-level
headway/wait-time view — over a Spring GraphQL read API.

Design reference:
[`docs/superpowers/specs/2026-09-05-prediction-generation-design.md`](superpowers/specs/2026-09-05-prediction-generation-design.md).
Schema migration: `src/main/resources/db/changelog/0003-predict.yaml`.
Builds directly on top of the AVL subsystem — see [`docs/avl.md`](avl.md) first.

---

## 1. Overview

```
AvlMatchProcessor.persist()  (Matched branch, per report)
        │
        ▼
PredictionService.onMatched()
        │
        ├─ crossing detection ──▶ travel_time_observation / kalman_travel_time_state (learn)
        │                    └──▶ fills actual_arrival_ts/actual_departure_ts on existing rows
        │                          └──▶ appends prediction_accuracy (predicted vs actual)
        │
        └─ generation ──────────▶ vehicle_prediction (upserted, current trip + up to 2 block-trips)
                                        │
                                    GraphQL: vehiclePredictions / stopPredictions / headway / predictionAccuracy
```

There is **no separate scheduler**. Predictions are computed inline, right
after each AVL match, inside `AvlMatchProcessor.persist(...)` — the same place
`vehicle_match`/`vehicle_state` are written. `PredictionService` is looked up
via `ObjectProvider<PredictionService>` so the AVL module has zero compile-time
dependency on `predict`, and predictions are a true no-op when
`transittrack.predict.enabled = false`.

Three pluggable algorithms compute an ETA for each stop path ahead of a
vehicle:

| Algorithm | Basis |
| --- | --- |
| `SCHEDULE_ADHERENCE` | scheduled arrival time, shifted by the vehicle's current `scheduleAdherenceSec` (held constant across the horizon) |
| `HISTORICAL_AVERAGE` | a running mean of observed travel time per `(trip_pattern_id, stop_path_index)`, accumulated stop by stop from the vehicle's current position |
| `KALMAN` | a scalar Kalman filter over the same observations, same accumulation, plus a `confidenceSec` |

Each `avl_feed` selects one designated algorithm (`predictionAlgorithm`) and a
**prediction mode**:

| Mode | Behaviour |
| --- | --- |
| `SINGLE` (default) | only the feed's `predictionAlgorithm` runs and is written — one `vehicle_prediction` row per stop |
| `EVALUATION` | all three algorithms run and each writes its own row — three rows per stop, for comparing algorithms side by side while onboarding a feed |

Headway/wait (§6) always aggregates a single designated algorithm per feed
(the feed's `predictionAlgorithm`, even in `EVALUATION` mode) so multiple
algorithms' rows for the same vehicle never double-count.

---

## 2. Configuration

The whole tree is under `transittrack.predict` and is **disabled by default**
(`enabled: false`) — no beans that touch predictions are created, and
`AvlMatchProcessor` runs exactly as it does without this module.

```yaml
transittrack:
  feed:
    feeds:
      - code: mbta
        # ...GTFS fields (see docs/gtfs.md)...
        avl:
          # ...AVL fields (see docs/avl.md)...
          prediction-algorithm: SCHEDULE_ADHERENCE   # SCHEDULE_ADHERENCE | HISTORICAL_AVERAGE | KALMAN, default SCHEDULE_ADHERENCE
          prediction-mode: SINGLE                    # SINGLE | EVALUATION, default SINGLE
  predict:
    enabled: false
    learn:
      max-plausible-travel-time-sec: 1800          # samples outside (0, this] are discarded, not learned from
      kalman-measurement-noise-sec2: 400.0         # ~20s std-dev of a single observation
      kalman-initial-variance-sec2: 3600.0         # ~60s std-dev before any observation
    retention:
      prediction-hours: 6
      accuracy-days: 30
      sweep-cron: "0 15 * * * *"                   # hourly, offset from the AVL sweep
```

`prediction-algorithm` and `prediction-mode` are per-feed fields on the nested
`avl` block of `transittrack.feed.feeds[]` (`FeedsProperties.AvlDef`), not under
`transittrack.predict` — one AVL feed maps to one prediction configuration,
mirroring how `assignment-mode` already works.

---

## 3. The learning loop

**Crossing detection** runs first, inside `PredictionService.onMatched`,
before any prediction is (re)generated:

- Fires only when the new match is on the **same trip** as the vehicle's
  previous match (`outcome.tripRowId == prev.tripRowId`) and has advanced
  (`outcome.stopPathIndex > prev.stopPathIndex`) — a block-advance or
  re-assignment produces no crossing.
- **Apportionment**: the elapsed wall time between the two reports is split
  across every stop path crossed since the last match, by each one's length
  share of the total distance crossed: `share_i = lengthM(i) / Σ lengthM(prev.stopPathIndex+1 .. outcome.stopPathIndex)`.
  Each `share_i * elapsed` becomes one observed travel-time sample for
  `(trip_pattern_id, i)`. A single-stop advance apportions 100% of the elapsed
  time to it; a multi-stop jump (a sparse feed that skipped a stop) splits it
  proportionally — an approximation, since it assumes uniform speed across the
  skipped stops.
- A sample outside `(0, learn.max-plausible-travel-time-sec]` (default 1800s)
  is discarded — logged, not learned from, not fatal.
- Every surviving sample feeds **both** learners below, **and** fills
  `actual_arrival_ts`/`actual_departure_ts` on the existing `vehicle_prediction`
  row for `(feed, vehicle, tripRowId, i)` if one exists — the same signal
  drives learning and the merged stop-time view (§4).

**Running average** (`travel_time_observation`, one row per
`(trip_pattern_id, stop_path_index)`): `newMean = mean + (sample - mean) / newCount`,
upserted natively (like `vehicle_state`).

**Kalman filter** (`kalman_travel_time_state`, one row per
`(trip_pattern_id, stop_path_index)`), a scalar filter on the travel-time
value itself:

```
gain = errorVariance / (errorVariance + measurementNoise)
estimateSec += gain * (sample - estimateSec)
errorVariance *= (1 - gain)
```

Both learners are **seeded from the schedule-derived
`travel_times_for_stop_path.travelTimeSec`** the first time they're read for a
given `(trip_pattern_id, stop_path_index)` with no row yet — `estimateSec`
starts there with an initial `errorVariance` of
`learn.kalman-initial-variance-sec2` — so `HISTORICAL_AVERAGE`/`KALMAN`
predictions are sane from the very first match, before any AVL observation has
landed, and only diverge from the schedule value once real observations
accumulate.

Both learner tables are keyed by `trip_pattern_id`, which is
**GTFS-revision-scoped**: a new revision creates new pattern ids, so learned
state naturally goes stale rather than migrating forward — reclaimed by the
retention scheduler's orphan sweep (§7). This is the same, already-accepted
limitation `travel_times_for_stop_path` itself has across revisions.

---

## 4. The merged stop-time model

One `vehicle_prediction` row per `(feed_id, vehicle_id, trip_row_id,
stop_path_index[, algorithm])`, upserted in place (native
`INSERT ... ON CONFLICT ... DO UPDATE`, same pattern as `AvlWriter.upsertState`):

- **Created on first match** for the vehicle's current trip (stop paths from
  its current position through the end of the trip) plus up to its next **2
  block-trips** (fewer than 2 remain in the block → predict as many as
  exist). Stop paths before the vehicle was first observed on the trip never
  get a row — there's nothing to predict (already past) and nothing to
  backfill as actual (the vehicle wasn't tracked yet).
- **The same row later gets `actual_arrival_ts`/`actual_departure_ts` filled
  in** by crossing detection (§3) once the vehicle passes that stop —
  `predicted_*`/`algorithm` are left untouched, so the row keeps both "what we
  predicted" and "what actually happened" for that one stop, which is what
  makes accuracy tracking (§5) possible and gives `StopPrediction` its merged
  view: scheduled (resolved live from `schedule_time`, not stored) + actual +
  predicted, in one row.
- A row that just got an actual filled in is no longer forward-looking, so
  `stopPredictions`/headway (§6) exclude it — only rows with
  `predicted_arrival_ts != null && actual_arrival_ts == null` count for those
  reads. (`vehiclePredictions` has no such filter — it shows the full trip
  history including already-crossed stops.)

---

## 5. Accuracy tracking

The moment crossing detection fills `actual_arrival_ts` on a
`vehicle_prediction` row that already had `predicted_arrival_ts` set, one
`prediction_accuracy` row is appended:

```
error_sec = actual_arrival_ts.epochSecond - predicted_arrival_ts.epochSecond   // negative = predicted early
```

This happens regardless of `predictionMode` — a `SINGLE`-mode feed still gets
one accuracy row per crossing, for its one active algorithm.
`predictionMode == EVALUATION` produces one accuracy row **per algorithm** per
crossing (since there are more `vehicle_prediction` rows to diff), which is
the whole point of evaluation mode: `predictionAccuracy` lets you compare
sample count, mean error, and mean absolute error across all three algorithms
on read, side by side, when deciding which one to run in `SINGLE` mode for a
newly-onboarded feed. `prediction_accuracy` is intentionally unaggregated in
storage — MAE/bias per algorithm is computed on read.

---

## 6. Headway / wait time

`HeadwayReadService` is a **read-time aggregation** over live
`vehicle_prediction` rows — no new learning, no new storage. For a given
`(feedCode, stopId, routeId, directionId)`:

1. Resolve the `stop_path_index` that `stopId` sits at in that route+direction's
   pattern(s), then collect every forward-looking `vehicle_prediction` row at
   that index (across every currently-matched vehicle on that route+direction),
   filtered to the feed's designated `predictionAlgorithm`, ordered by
   predicted arrival.
2. **Wait time** (`waitSec`) = time from now until the soonest arrival in that
   list.
3. **Gaps** (`gapsSec`) = consecutive differences between arrivals — empty
   when fewer than two vehicles are currently approaching that stop.
4. **Scheduled headway** (`scheduledHeadwaySec`) = the same gap computation
   over `schedule_time` across every trip of the pattern(s) serving that stop
   — a static baseline for comparison, independent of which vehicles are
   currently matched.

If this live join doesn't scale, the design flags a materialized headway
snapshot table as the natural next step — not built preemptively.

---

## 7. Retention

`PredictionRetentionScheduler` (`@ConditionalOnProperty("transittrack.predict.enabled", "true")`)
runs on `retention.sweep-cron` (hourly, offset from the AVL sweep):

| Table | Pruned when | Note |
| --- | --- | --- |
| `vehicle_prediction` | `computed_at` older than `retention.prediction-hours` (default 6h) | predictions go stale fast |
| `prediction_accuracy` | `created_at` older than `retention.accuracy-days` (default 30d) | kept far longer — this is what an operator reviews to pick an algorithm |
| `travel_time_observation` | `trip_pattern_id` no longer in `trip_patterns` | orphan sweep, reclaims state from a superseded/pruned GTFS revision |
| `kalman_travel_time_state` | `trip_pattern_id` no longer in `trip_patterns` | same orphan sweep |

---

## 8. GraphQL read API

```graphql
vehiclePredictions(feedCode: String!, vehicleId: String!): [StopPrediction!]!
stopPredictions(feedCode: String!, stopId: String!, routeId: String, directionId: Int): [StopPrediction!]!
headway(feedCode: String!, stopId: String!, routeId: String!, directionId: Int): Headway!
predictionAccuracy(feedCode: String!, algorithm: String, sinceDays: Int = 7): [PredictionAccuracySummary!]!
```

- `vehiclePredictions` — every `vehicle_prediction` row for that vehicle's
  current trip (as recorded on `vehicle_state`), ordered by `stopPathIndex`,
  including already-crossed stops. In `EVALUATION` mode this returns one
  `StopPrediction` per `(stopPathIndex, algorithm)` combination.
- `stopPredictions` — one row per currently-matched vehicle still approaching
  a given stop (a rider-facing "next arrivals" list; the finer-grained input
  `headway` aggregates).
- `headway` — see §6.
- `predictionAccuracy` — see §5.

```graphql
{
  vehiclePredictions(feedCode: "mbta-vp", vehicleId: "bus-1") {
    stopPathIndex predictedArrival algorithm actualArrival
    stop { stopId }
  }
}
```

---

## 9. Known limitations

1. **Stop-path index 0 is currently unreachable by the AVL matcher for every
   trip pattern.** The AVL match horizon starts at the *next unvisited stop*
   — a vehicle positioned anywhere along a trip's shape, even a couple of
   percent in, is already matched to stop-path index 1, never 0. This is a
   pre-existing issue in the AVL subsystem (`PatternBuild.kt` /
   `AvlMatchContext.buildGeometry`), not introduced or fixed by this plan. Its
   direct consequence here: a trip's very first stop can never receive a
   live-generated prediction. Tracked as a follow-up against the AVL module.
2. **`vehicle_prediction`'s uniqueness key has no `service_date` column** —
   it's `(feed_id, vehicle_id, trip_row_id, stop_path_index, algorithm)`.
   Since a GTFS `trip_row_id` recurs every day its calendar runs, an
   `actual_arrival_ts` filled in on one service day could in principle survive
   into the next occurrence of the same trip if retention hasn't pruned it
   yet. In practice this is mitigated by `retention.prediction-hours`
   defaulting to 6h, well under the typical ~24h interval between recurrences
   — **operators should keep `prediction-hours` well under the shortest
   trip-repeat interval** for that mitigation to hold.
3. **Learned state (both learner tables) resets on every GTFS revision
   change** — pattern ids change with the revision, so `travel_time_observation`
   and `kalman_travel_time_state` rows go orphaned and are reclaimed rather
   than migrated forward. Same accepted limitation `travel_times_for_stop_path`
   already has.
4. **`HISTORICAL_AVERAGE`/`KALMAN` overestimate the arrival at the vehicle's
   very next stop.** The horizon deliberately re-includes the vehicle's
   current stop path (so it always carries a fresh prediction), and the walk
   anchors `lastTs` at the vehicle's current timestamp — but the vehicle
   already has a known `distanceAlongTripM` into that segment, and these two
   algorithms add the *full* learned/seeded travel time for it anyway, rather
   than pro-rating for the distance already covered. `SCHEDULE_ADHERENCE` is
   unaffected (it's schedule-anchored, not accumulation-based). The result is
   a small, systematic lateness bias at the very next stop specifically for
   the two algorithms whose purpose is to be more accurate than schedule
   adherence — a natural companion to limitation #1 above (both stem from not
   using `distanceAlongTripM` for partial-segment math). Not fixed in this
   plan; a future refinement could scale the first segment's travel time by
   the fraction remaining.

Deferred entirely (see the design spec, §1/§12): emitting GTFS-RT `TripUpdate`
from these predictions, and a materialized headway snapshot table (only
needed if the live aggregation in §6 proves too slow at scale).

See also: [docs/avl.md](avl.md), [docs/gtfs.md](gtfs.md), [docs/schedule.md](schedule.md).
