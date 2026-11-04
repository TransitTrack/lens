# Prediction generation

This subsystem (`eu.transittrack.predict`) computes per-stop arrival/departure
**predictions** for every AVL-matched vehicle, tracks their accuracy against
what actually happened, and exposes both — plus a derived route-level
headway/wait-time view — over a Spring GraphQL read API.

Schema migrations: `app/src/main/resources/db/changelog/0003-predict.yaml`, reshaped
by 0010/0012/0015/0016/0017 (feed-code keys, sequences, pattern keys, learning
buckets), 0018/0019 (claim index, accuracy horizons), and 0020 (source freshness,
crossing quality, persistent frequency runs and shared accuracy snapshots), and
0021 (`prediction_model`, range and late columns for SMILE_GBM).
Builds directly on top of the AVL subsystem — see [`docs/avl.md`](avl.md) first.

---

## 1. Overview

```mermaid
flowchart TD
    A["AvlMatchProcessor.persist()<br/>(Matched branch, per report)"] --> B[("vehicle_match row written<br/>prediction_status = PENDING")]
    B -->|"independent schedule<br/>transittrack.predict.run.interval-ms"| C["PredictionProcessor.processFeed()<br/>claims a batch of PENDING rows per feed<br/>(transittrack.predict.run.claim-batch-size)"]
    C --> D["PredictionService.onMatched()"]

    D --> E["stop-pass detection"]
    E --> F[("travel_time_observation /<br/>kalman_travel_time_state (learn)")]
    E --> G["fills actual_arrival_ts / actual_departure_ts<br/>on existing rows"]
    E --> H[("prediction_checkpoint scored into<br/>prediction_accuracy, per horizon")]

    D --> I["generation"]
    I --> J[("vehicle_prediction<br/>upserted, current trip + up to 2 block-trips")]
    I --> M[("prediction_checkpoint<br/>shared fixed-slot snapshots, including missing output")]
    J --> K(["GraphQL: vehiclePredictions / stopPredictions /<br/>headway / predictionAccuracy"])

    D --> L["vehicle_match row marked DONE (or FAILED)<br/>decoupled from the AVL match cycle"]
```

Prediction generation runs on its **own independent scheduler**,
`PredictionProcessor`, decoupled from `AvlMatchProcessor`. Every successful
AVL match writes exactly one `vehicle_match` row with `prediction_status =
PENDING`; `PredictionProcessor` polls each enabled feed on its own cadence
(`transittrack.predict.run.interval-ms`, default 5000ms), claims a batch of
that feed's PENDING rows (`transittrack.predict.run.claim-batch-size`, default
500), dispatches each to `PredictionService.onMatched` in order, and marks
each row `DONE` or `FAILED`. A feed with a large backlog just takes longer to
drain, or drains across several cycles — nothing is silently dropped under
load, unlike the fixed-capacity executor this replaces. Predictions are a true
no-op when `transittrack.predict.enabled = false`: `PredictionProcessor` isn't
even registered as a bean, and PENDING rows simply accumulate unprocessed in
`vehicle_match` (see the `transittrack.prediction.pending.matches` gauge).

Four pluggable algorithms compute an ETA for each stop path ahead of a
vehicle:

| Algorithm | Basis |
| --- | --- |
| `SCHEDULE_ADHERENCE` | scheduled arrival time, shifted by the vehicle's current `scheduleAdherenceSec`; early running is absorbed at a trip's first stop, lateness by the layover before the next block trip. Stops at the first frequency-based trip of the horizon (§4) |
| `HISTORICAL_AVERAGE` | a windowed running mean of observed stop-to-stop times per pattern, stop path and day-type/time-band bucket, accumulated stop by stop from the vehicle's current position |
| `KALMAN` | a scalar Kalman filter over the same observations, same accumulation, plus a `confidenceSec` (§3) |
| `SMILE_GBM` | gradient-boosted trees (Smile) trained nightly per AVL feed on past stop crossings, same accumulation; also an ~80% arrival range and a late assessment (§9) |

Each `avl_feed` lists the algorithms to run (`predictionAlgorithms`, default
`[SCHEDULE_ADHERENCE]`). Every listed algorithm writes its own
`vehicle_prediction` row per stop and is scored for accuracy, so listing more
than one compares them side by side. The **first** listed algorithm is the
feed's designated one: `stopPredictions` and `headway` (§6) use it when no
`algorithm` argument is given, so multiple algorithms' rows for the same
vehicle never double-count. If it has no live rows for the stop, the next
listed algorithm is tried, and so on (§9.7); the whole board then comes from
that one algorithm.

Strategies run **before** the write transaction opens, each isolated from the
others: one failing strategy neither drops the other algorithms' predictions
nor rolls back the stop passes and learning of the same report. Its failure is
rethrown once everything else is committed, so the `vehicle_match` row is still
marked `FAILED`.

Generation requires a source report no older than `run.max-source-age-sec`
(default 600). A backlog is still processed for crossings and learning, but
reprocessing old GPS data does not turn it into a live forecast. Source report
time, GTFS service date and processing time are stored separately.

---

## 2. Configuration

The whole tree is under `transittrack.predict`. The configuration class defaults
to `enabled: false`, but the shipped `application.yaml` explicitly enables it.
Setting it to `false` disables generation and prediction housekeeping; the read
API remains available for stored data.

```yaml
transittrack:
  feed:
    feeds:
      - code: mbta
        # ...GTFS fields (see docs/gtfs.md)...
        avl:
          # ...AVL fields (see docs/avl.md)...
          prediction-algorithms: [ SCHEDULE_ADHERENCE, KALMAN ]   # first one is the feed's designated algorithm
  predict:
    enabled: true
    run:
      interval-ms: 5000                            # PredictionProcessor's own poll cadence, independent of avl.match.match-interval-ms
      claim-batch-size: 500
      prediction-min-change-sec: 10                # an open prediction moving less than this is not rewritten...
      prediction-refresh-sec: 120                  # ...unless its computed_at is older than this
      max-source-age-sec: 600                      # generation and live reads use source GPS age; refresh must be below this
    learn:
      max-plausible-travel-time-sec: 1800          # samples outside (0, this] are discarded, not learned from
      kalman-measurement-noise-sec2: 400.0         # ~20s std-dev of a single observation
      kalman-initial-variance-sec2: 3600.0         # ~60s std-dev before any observation
      kalman-process-noise-sec2: 25.0              # drift allowed per sample; keeps the gain from decaying to zero
      kalman-variance-growth-sec2-per-hour: 400.0  # additional uncertainty while evidence ages
      max-reliable-report-gap-sec: 120            # longer GPS intervals still record actuals, but aren't trusted labels
      history-window: 50                           # past this many samples each new one weighs 1/window
      history-prior-samples: 5                     # the schedule seed counts as this many samples in the average
      outlier-min-deviation-sec: 60.0              # a sample is clipped to estimate ± max(this,
      outlier-max-relative-deviation: 1.0          #   this × estimate) before either learner takes it in
      min-bucket-samples: 10                       # a bucket is trusted over its roll-up from this many samples
    accuracy:
      horizons-sec: [ 60, 180, 300, 600, 900, 1200, 1800 ]   # actual lead-time upper bounds (§5)
      sample-interval-sec: 60                      # shared source-report slots, independent of each algorithm's ETA
    retention:
      prediction-hours: 6
      accuracy-days: 30
      raw-crossing-days: 90
      sweep-cron: "0 15 * * * *"                   # hourly, offset from the AVL sweep
    smile:                                         # SMILE_GBM (§9)
      train-cron: "0 30 3 * * *"                   # nightly training, PREDICTOR role, once per cluster
      refresh-ms: 300000                           # every node checks prediction_model for a newer model this often
      min-training-rows: 2000                      # fewer usable crossings: no new model, the previous one stays
      max-training-rows: 100000                    # newest crossings taken
      training-days: 60                            # from this many days back; must be <= retention.raw-crossing-days
      trees: 200
      max-depth: 6
      shrinkage: 0.1
      subsample: 0.7
      low-quantile: 0.1                            # range quantiles: 0.1..0.9 is the ~80% range
      high-quantile: 0.9
    late:                                          # SMILE_GBM late assessment (§9.4)
      schedule-tolerance-sec: 300                  # late = more than this past the scheduled arrival...
      headway-fraction: 0.5                        # ...or more than this share of the scheduled headway, if smaller
```

`prediction-algorithms` is a per-feed field on the nested `avl` block of
`transittrack.feed.feeds[]` (`FeedsProperties.AvlDef`), not under
`transittrack.predict` — mirroring how `assignment-mode` already works.

---

## 3. The learning loop

**Stop-pass detection** runs first, inside `PredictionService.onMatched`,
before any prediction is (re)generated:

- Fires only when the new match is on the **same trip** as the vehicle's
  previous match and has moved forward along it — a block-advance or
  re-assignment produces no pass.
- Stop `k` sits at the **end** of stop path `k`. It is passed once the
  vehicle's along-trip distance moves from before it to at/after it; the pass
  time is interpolated linearly by distance between the two reports. The
  first stop (stop path 0, zero-length) is "passed" when the vehicle **leaves**
  it, i.e. moves beyond `FIRST_STOP_RADIUS_M` (30 m) of the trip start; its
  pass time is the midpoint of the interval. The last stop counts as reached
  `LAST_STOP_RADIUS_M` (30 m) before the end of the trip (no closer than
  halfway along its stop path), since a vehicle laying over at the terminus
  usually reports just short of the shape's end. Once reached, the next
  prediction run no longer predicts it.
- Each pass is appended to `avl_stop_crossing` (also the optimizer's input).
  GPS jitter carrying a vehicle across the same stop twice counts once: a stop
  already passed within the current service day/run and
  `max-plausible-travel-time-sec` is skipped.
- A pass whose previous stop on the same trip was also passed yields one
  **stop-to-stop sample**: the time from passing stop `k-1` to reaching stop
  `k` (so it includes the dwell at `k-1`). A sample outside
  `(0, learn.max-plausible-travel-time-sec]` is not learned from, nor is one
  whose two stops were passed between the **same** two reports: both pass
  times are interpolated over one interval, so the "sample" would only echo
  the uniform-speed assumption (the passes are still recorded and fill
  actuals).
- `report_gap_sec` records the report interval, rounded **up** to whole seconds.
  A gap above `learn.max-reliable-report-gap-sec` marks the actual unreliable.
  Learning requires reliable intervals at **both** endpoints; a reliable new
  crossing cannot rehabilitate an unreliable or legacy unknown endpoint.
  Unreliable actuals remain visible, but their checkpoints are discarded without
  accuracy scoring. `transittrack.prediction.crossings.unreliable` counts them.
- Every pass fills `actual_arrival_ts` (`actual_departure_ts` for stop 0) on
  the vehicle's `vehicle_prediction` rows for that stop, and scores its
  accuracy checkpoints (§5).

**Keys and buckets.** Both learners are keyed by
`(gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band)`.
`pattern_key` is the pattern's stable key (route, shape, stop list), so
learning survives GTFS revision swaps that keep a pattern. The bucket is the
day type (`WEEKDAY`/`SATURDAY`/`SUNDAY`) and service-day time band (`NIGHT`,
`MORNING_PEAK` 06–09, `MIDDAY` 09–15, `AFTERNOON_PEAK` 15–19, `EVENING` 19–24)
of the moment the vehicle **left the previous stop**. Each sample updates its
bucket plus two roll-ups — the whole day type (`time_band = ALL`) and
everything (`ALL`/`ALL`). Predictions look a stop path up the same way and fall
back to the coarser roll-up while a bucket has fewer than
`learn.min-bucket-samples` samples.

**Outlier clipping.** Before either learner takes a sample in, it is clipped
to `estimate ± max(outlier-min-deviation-sec, outlier-max-relative-deviation × estimate)`:
a bus held 20 minutes at a stop moves the estimate a little rather than by
minutes, while a lasting change (road works, a detour) still gets through over
a few samples. The very first sample of a bucket is never clipped — the
schedule seed it would be measured against is no learned value.

**Running average** (`travel_time_observation`):
`mean += (sample - mean) / min(sampleCount + history-prior-samples, history-window)`.
The schedule seed counts as `history-prior-samples` samples, so the first few
observations blend with it rather than the first one replacing it; past the
window each new sample keeps weight `1/history-window`, so the mean keeps
tracking changing conditions.

**Kalman filter** (`kalman_travel_time_state`), a scalar filter on the
travel-time value itself:

```
agedVariance = errorVariance + max(0, evidenceAgeHours) * varianceGrowthPerHour
prior = agedVariance + processNoise         // no process noise on the first sample
gain = prior / (prior + measurementNoise)
estimateSec += gain * (clippedSample - estimateSec)
errorVariance = prior * measurementNoise / (prior + measurementNoise)
```

With the defaults the gain settles around 0.2.

A `KALMAN` prediction's `confidenceSec` is an estimated **one-standard-deviation
spread**, not a confidence percentage. Each learned leg contributes its state
variance, measurement noise and elapsed-evidence-age variance. An unlearned
schedule-fallback leg contributes `initialVariance + measurementNoise` (4000
sec² by default), rather than silently adding zero uncertainty. A partial leg's
variance scales by `remainingFraction²`; independent-leg variances accumulate.
The defaults are conservative modeling assumptions, not empirically calibrated
production values; §5 exposes their observed coverage.

Both learners are **seeded from the schedule-derived stop-to-stop time**
(`travel_times_for_stop_path` travel time plus the dwell at the previous stop)
when a bucket has no row yet — the Kalman state starts there with
`learn.kalman-initial-variance-sec2` — so `HISTORICAL_AVERAGE`/`KALMAN`
predictions are sane from the very first match.

**Consistency.** All reads and writes of one report go through a single
`PredictionWriter.inTransaction` call: one `REQUIRES_NEW` Exposed transaction per report, independent
of the caller's. Learner rows are read with `SELECT ... FOR UPDATE`
in a fixed order (stop path ascending, then fine to coarse bucket, observation
before Kalman state). A transaction-scoped advisory lock per GTFS feed/pattern
also protects initialization when there is no row to lock yet. Concurrent first
samples are therefore retained without inserting artificial seed observations.
Learner freshness uses the newest source evidence timestamp, not processing time,
and older samples cannot move that timestamp backwards.

**Batching.** A report's writes are batched rather than issued row by row: one
locking `SELECT` per learner table for every bucket the report's samples touch,
then one JDBC batch of upserts per table; one batch each for crossings,
predictions, checkpoints and accuracy rows; and one `UPDATE` filling every
passed stop's actuals. Schedule seeds (`travel_times_for_stop_path`) are read
through the `avlTravelTimes` cache, one map per pattern and revision.

Learner rows whose `pattern_key` no revision of their GTFS feed still has are
reclaimed by the retention scheduler's orphan sweep (§7).

---

## 4. The merged stop-time model

One `vehicle_prediction` row per `(feed_code, vehicle_id, trip_row_id,
stop_path_index, algorithm)`, upserted in place (native
`INSERT ... ON CONFLICT ... DO UPDATE`, same pattern as `AvlWriter.upsertState`):

- **Created on first match** for the vehicle's current trip (stop paths from
  its current position through the end of the trip) plus up to its next **2
  block-trips** (fewer than 2 remain in the block → predict as many as
  exist). A flagged deadhead transition ends the horizon: there is no modeled
  transfer duration to support a forecast across it. Stop paths before the vehicle was first observed on the trip never
  get a row — there's nothing to predict (already past) and nothing to
  backfill as actual (the vehicle wasn't tracked yet).
- **The same row later gets `actual_arrival_ts`/`actual_departure_ts` filled
  in** by stop-pass detection (§3) once the vehicle passes that stop —
  `predicted_*`/`algorithm` are left untouched, so the row keeps both "what we
  predicted" and "what actually happened" for that one stop, which is what
  makes accuracy tracking (§5) possible and gives `StopPrediction` its merged
  view: scheduled (resolved live from `schedule_time`, not stored) + actual +
  predicted, in one row.
- A row that just got an actual filled in is no longer forward-looking, so
  `stopPredictions`/headway (§6) exclude it — only rows with
  `predicted_arrival_ts != null && actual_arrival_ts == null && actual_departure_ts == null` count for those
  reads. (`vehiclePredictions` has no such filter — it shows the full trip
  history including already-crossed stops.)
- **A row with an actual is not overwritten** by later prediction runs (a
  vehicle jittering back before a stop it passed would otherwise wipe the
  pass). A different persisted `service_date` resets the row and its actuals
  immediately; expiry remains a fallback for older rows without that date.
  Older source reports cannot overwrite newer predictions or delete their
  remaining horizon.
- **An open row is only rewritten when it changed**: its block or pattern
  changed, a predicted time appeared or disappeared, or a predicted time moved
  by at least `run.prediction-min-change-sec` — or its `computed_at` is older
  than `run.prediction-refresh-sec`, refreshing its source evidence. Live readers
  use `source_report_ts` against `run.max-source-age-sec`, never `computed_at`.
  This spares most rows the write a
  vehicle's every report would otherwise cost.

Learned arrival-to-arrival duration is split into travel and preceding dwell in
the schedule's proportions. The previous departure and next arrival use that
same split, so a shortened learned leg cannot put departure after the following
arrival. Future block-trip metadata survives omission of a reached terminal,
allowing the next trip's scheduled layover to absorb previous lateness.

**Frequency-based trips** (GTFS `frequencies.txt`) have no clock times, so
`SCHEDULE_ADHERENCE` predicts none of their stops (nor anything after them in
the horizon), and the learned algorithms don't hold a vehicle at their first
stop until a scheduled departure. A frequency trip runs as repeated laps on
the same trip row. `prediction_frequency_run` persists the current service date,
run boundary, descriptor departure and latest report/distance per feed/vehicle/trip,
so returning after another assignment does not reload the old lap.
A changed service date/descriptor departure or recognized backwards reset starts
a new run **before its first crossing**. Previous predictions and pending
checkpoints are cleared, while historical crossings remain intact and are
excluded from current-run learning. Matching still has the limitation in §10.

---

## 5. Accuracy tracking

Scoring only a stop's latest prediction — refreshed on every match until the
vehicle gets there — would measure predictions made seconds before arrival,
which every algorithm gets nearly right. Accuracy is therefore sampled at
fixed shared snapshots, then grouped into **actual lead-time horizons**
(`accuracy.horizons-sec`, default 1, 3, 5, 10, 15, 20 and 30 minutes):

1. **Capture.** Source report time is rounded down to
   `accuracy.sample-interval-sec` (default 60 seconds). The first report cohort
   in each slot is retained for every horizon target and configured algorithm.
   Missing output, including strategy failure, is an explicit null forecast.
   Later reports cannot fill a cohort with different source/production times.
   Already-passed stops get no new snapshot.
2. **Timestamp.** `computed_at` on a checkpoint is its source report time;
   `produced_at` is captured just before persistence, after generation and
   crossing/learner locking. Source freshness is checked again there.
3. **Score.** At a reliable crossing, lead is `actual − produced_at`, never
   `predicted − report`. Snapshots from another service date, issued after the
   crossing, or beyond the longest horizon are excluded. In each non-overlapping
   lead-time bin, the earliest issued cohort supplies **all** algorithms'
   results. Stop 0 uses departure; other stops use arrival. Consumed checkpoints
   are deleted. Accuracy retains source time as `predicted_at` and issuance time
   separately as `produced_at`:

```
error_sec = actual − predicted     // positive = the vehicle got there later than predicted
```

Missing output has null prediction/error fields, not fabricated zero error.
Snapshots older than the longest scored lead plus one sample interval are
pruned as new ones are captured; retention also removes abandoned checkpoints.

`predictionAccuracy` compares the feed's configured algorithms by default, or
one explicitly requested algorithm. Cohorts must contain a recorded outcome
for every compared algorithm at identical source/production times. `eligibleCount`
is that denominator; `sampleCount` and `predictionCoverage` show available
forecasts. MAE, signed error and P90/P95 use **only fully paired cohorts**,
reported as `pairedSampleCount`. Unavailable comparisons return null error
statistics. Overall percentiles pool raw paired errors across horizons; they
are not averages of horizon percentiles.

`byHorizon` separates near-term and longer-range performance. Optional route,
direction, day-type and source-observation-time-band filters allow contextual
comparisons. `confidenceCoverage` measures the fraction of available forecasts
whose absolute error is within their estimated one-SD spread;
`confidenceSampleCount` gives that separate denominator. `rangeCoverage` is the
fraction of scored forecasts with a predicted range (only `SMILE_GBM` gives
one, §9.3) whose actual arrival fell inside it, with `rangeSampleCount` as its
denominator; it is null when no forecast had a range. Accuracy rows also store
the range bounds and, for `SMILE_GBM`, the predicted and the actual late flag
(`predicted_late`, `actual_late`: the actual arrival judged by the same rule,
§9.4).

Migration 0020 clears only old **unscored checkpoints**. Scored history remains
stored (and usable as raw optimizer evidence), but legacy ETA-selected rows are
excluded from the new comparison because they lack actual-lead/issuance markers.
Comparable summaries therefore refill from newly observed crossings. The
`transittrack.prediction.error` / `.absolute.error` metrics are tagged only by
`feed` and `algorithm` (pooling every horizon) to keep their cardinality flat;
use `byHorizon` for the per-horizon breakdown.

---

## 6. Headway / wait time

`HeadwayReadService` is a **read-time aggregation** over live
`vehicle_prediction` rows — no new learning, no new storage. For a given
`(feedCode, stopId, routeId, directionId)`:

1. Resolve the `stop_path_index` that `stopId` sits at in that route+direction's
   pattern(s), then collect every forward-looking `vehicle_prediction` row at
   that index (across every currently-matched vehicle on that route+direction),
   filtered to the requested algorithm (default: the feed's first listed one), ordered by
   predicted arrival.
2. **Wait time** (`waitSec`) = time from now until the soonest arrival in that
   list.
3. **Gaps** (`gapsSec`) = consecutive differences between arrivals — empty
   when fewer than two vehicles are currently approaching that stop.
4. **Scheduled headway** (`scheduledHeadwaySec`) = the **average** gap
   between the trips of the pattern(s) scheduled at that stop from one hour
   before the first predicted arrival to one hour after the last (now ± 1 h
   when nothing is predicted), counting only trips whose service runs on that
   day (the previous service day too, for times past 24:00). Frequency-based
   trips have no clock times and are left out. `null` when fewer than two
   trips fall in the window.

If this live join doesn't scale, the design flags a materialized headway
snapshot table as the natural next step — not built preemptively.

---

## 7. Retention

`PredictionRetentionScheduler` (`@ConditionalOnProperty("transittrack.predict.enabled", "true")`)
runs on `retention.sweep-cron` (hourly, offset from the AVL sweep):

| Table | Pruned when | Note |
| --- | --- | --- |
| `vehicle_prediction` | `computed_at` older than `retention.prediction-hours` (default 6h) | predictions go stale fast |
| `prediction_checkpoint` | `computed_at` older than `retention.prediction-hours` | never scored: the vehicle didn't reach (or wasn't seen passing) the stop |
| `prediction_frequency_run` | `last_report_ts` older than `retention.prediction-hours` | idle run state; raw crossing history is retained independently |
| `prediction_accuracy` | `created_at` older than `retention.accuracy-days` (default 30d) | kept far longer — this is what an operator reviews to pick an algorithm |
| `avl_stop_crossing` | `observed_at` older than `retention.raw-crossing-days` (default 90d) | raw passes, input to schedule optimization |
| `travel_time_observation` | no stored revision of its GTFS feed has a pattern with its `pattern_key` | orphan sweep; a revision swap that keeps a pattern keeps its learning |
| `kalman_travel_time_state` | same | same orphan sweep |

---

## 8. GraphQL read API

```graphql
vehiclePredictions(feedCode: String!, vehicleId: String!, algorithm: PredictionAlgorithm): [StopPrediction!]!
stopPredictions(feedCode: String!, stopId: String!, routeId: String, directionId: Int, algorithm: PredictionAlgorithm): [StopPrediction!]!
headway(feedCode: String!, stopId: String!, routeId: String!, directionId: Int, algorithm: PredictionAlgorithm): Headway!
predictionAccuracy(feedCode: String!, algorithm: PredictionAlgorithm, sinceDays: Int = 7,
  routeId: String, directionId: Int, dayType: PredictionDayType, timeBand: PredictionTimeBand): [PredictionAccuracySummary!]!
tripLateness(feedCode: String!, vehicleId: String!, algorithm: PredictionAlgorithm): TripLateness
```

- `vehiclePredictions` — every `vehicle_prediction` row for that vehicle's
  current trip (as recorded on `vehicle_state`), ordered by `stopPathIndex`,
  including already-crossed stops; one `StopPrediction` per
  `(stopPathIndex, algorithm)` unless `algorithm` is given.
- `stopPredictions` — one row per trip still approaching a given stop (a
  rider-facing "next arrivals" list; the finer-grained input `headway`
  aggregates), restricted to the requested AVL feed. A trip predicted by two
  vehicles at once (the one running it, and one with it later in its block)
  counts once: the vehicle actually on the trip wins, otherwise the most
  recent source report. The deduplicated result is explicitly ordered by arrival.
- `headway` — see §6.
- `predictionAccuracy` — see §5. Summary and horizon rows expose availability,
  paired sample counts, nullable error/percentile metrics, one-SD coverage and
  range coverage (`rangeCoverage`, `rangeSampleCount`).
- `tripLateness` — the late assessment at the vehicle's next stop, next
  timepoint and terminal; see §9.6.
- `StopPrediction` carries `rangeLowSec`/`rangeHighSec` and
  `late`/`lateProbability`, null for every algorithm but `SMILE_GBM` (§9).
- `StopPrediction` includes `sourceReportTs`, `actualReportGapSec` and
  `actualReliable`. The vehicle timeline uses departure for stop 0 and warns on
  unreliable actuals. Its default algorithm follows feed configuration, not
  an unqualified global-MAE ranking.

```graphql
{
  vehiclePredictions(feedCode: "mbta-vp", vehicleId: "bus-1") {
    stopPathIndex predictedArrival algorithm actualArrival
    stop { stopId }
  }
}
```

---

## 9. SMILE_GBM (gradient boosting)

`SMILE_GBM` predicts each stop-to-stop time with gradient-boosted regression
trees ([Smile](https://haifengl.github.io/) `GradientTreeBoost`, quantile loss),
trained per AVL feed on that feed's own past stop crossings. Where
`HISTORICAL_AVERAGE` and `KALMAN` learn one number per pattern, stop path and
bucket, one model covers the whole feed and can combine its inputs (a route's
evening legs run long on Saturdays, the long legs of one direction stretch in
the morning peak), so it helps most on feeds with a lot of history and
patterns whose learner buckets fill slowly. It is also the only algorithm that
gives an arrival **range** and a **late assessment**.

Predictions are accumulated stop by stop exactly as for the learned
algorithms (`walkHorizonWith`: same first-stop hold, same dwell split, §4),
with the model's median as each leg's time. The walk ends at a stop path the
model can't estimate (no schedule seed, rejected features, non-finite output);
with no model for the feed it predicts nothing at all.

### 9.1 Features

One sample per observed stop-to-stop traversal, the same `StopToStopFeatures`
in training and serving:

| Feature | Meaning |
| --- | --- |
| `seedSec` | the schedule's stop-to-stop time (travel time plus the previous stop's dwell, §3) |
| `routeIndex` | the route, as an index fixed when the model was trained; a route it wasn't trained on counts as unknown |
| `directionId` | GTFS direction |
| `dayType`, `timeBand` | of the moment the vehicle left the previous stop, as for the learner buckets (§3) |
| `tripStartSec` | the trip's scheduled start, seconds into the service day |
| `stopPathIndex` | position of the stop along the pattern |

The vehicle's **current adherence is deliberately not a feature**:
`avl_stop_crossing`, the training data, doesn't store it, and a feature seen
only at serving time would be meaningless to the model.

### 9.2 Training

- `SmileTrainingJob` runs on `smile.train-cron` (default `0 30 3 * * *`,
  nightly at 03:30) on the **`PREDICTOR` role** only, once per cluster under the
  ShedLock lock `predict-smile-train`. It trains every enabled AVL feed that
  lists `smile_gbm` in its `prediction-algorithms`.
- Training data: up to `smile.max-training-rows` (default 100 000) of the
  feed's **newest** `avl_stop_crossing` rows from the last
  `smile.training-days` (default 60; must not exceed
  `retention.raw-crossing-days`) that carry a stop-to-stop time within
  `learn.max-plausible-travel-time-sec` and a report gap within
  `learn.max-reliable-report-gap-sec` (or an unknown one, §3). Crossings
  without a schedule seed are skipped. Three models are fitted: the median
  and the `smile.low-quantile`/`smile.high-quantile` quantiles (0.1/0.9), with
  `trees`, `max-depth`, `shrinkage` and `subsample` as configured.
- With fewer than `smile.min-training-rows` (default 2 000) usable crossings
  no model is trained; a feed that already has one keeps it. A failed run also
  keeps the previous model.
- The model is stored per feed (by `avl_feed.code`) in `prediction_model`, then
  published to the training node's `SmileModelStore`. Every node reloads
  newer stored models on startup and every `smile.refresh-ms` (default 5 min).
- **No model exists until the first nightly run.** A feed that just got
  `smile_gbm` has no SMILE predictions until then (and until it has
  `min-training-rows` crossings); its other configured algorithms keep
  serving meanwhile (§9.7).
- The newest-N cap takes the most recent crossings regardless of day type, so
  on a very busy feed the window can end up covering only the last few days,
  under-representing weekends (and Saturday/Sunday predictions lean on
  weekday patterns). The default keeps training memory small (below); raise
  `max-training-rows`, together with the heap, if a feed's window is too short.
- **Cost.** Only the columns training uses are read, streamed from the
  database into primitive arrays. Measured with the default settings (200
  trees, depth 6): a feed with 100 000 crossings adds roughly 30–50 MB of heap
  at its peak and fits in ~30 s of one core; 200 000 needs ~70–90 MB and ~60 s.
  Both grow linearly with the row count. Fits run one at a time on a single
  low-priority daemon thread (including Smile's internal parallel split
  search), so a fit takes at most one core and never a virtual-thread carrier
  the prediction loop needs; the ShedLock lock is held until every feed is done.
- **Heap sizing.** The JVM's default maximum heap is 25% of the container
  memory (256 MB under the chart's 1 Gi limit). When a feed lists `smile_gbm`,
  give the `PREDICTOR` node room for a fit on top of its usual load, e.g.
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50` (512 MB under a 1 Gi limit), and
  raise it further with `max-training-rows` or `trees`. Every node that
  predicts also holds each feed's model (~1 MB serialized, a few MB in memory).

### 9.3 Range

Each prediction except stop path 0 gets a range, `rangeLowSec`/`rangeHighSec`:
**about an 80% interval**, in signed seconds relative to `predictedArrival`
(`predictedArrival + rangeLowSec` to `predictedArrival + rangeHighSec`). Each
leg's spread is taken from the gap between its 10th and 90th percentile
(`(high - low) / (2 × 1.2816)`, a standard deviation); the legs' variances
accumulate along the walk like the Kalman confidence, giving `confidenceSec`,
and the range is `± round(1.2816 × confidenceSec)` — so it is **symmetric**
(`rangeLowSec = -rangeHighSec`) and widens with the horizon. Stop path 0 is a
departure, not an arrival, and gets neither range nor late assessment.

`predictionAccuracy.rangeCoverage` (§5) checks the calibration: a healthy
range has coverage near **0.8**. Much lower means the range is too narrow,
much higher too wide.

### 9.4 Late assessment

A prediction is **late** when the predicted delay against the scheduled
arrival (the departure time where the stop has no arrival time) is strictly
greater than the margin:

```
margin = late.schedule-tolerance-sec                                        (default 300)
margin = min(late.schedule-tolerance-sec,
             late.headway-fraction × scheduled headway)   when a scheduled headway is known (default 0.5)
late   = delay > margin
lateProbability = P(delay > margin), delay ~ Normal(predicted delay, confidenceSec)
```

The scheduled headway is the one the `headway` read model computes (§6) at
that stop, around the scheduled arrival: on a frequent route a delay of half a
headway already bunches vehicles, while a long-headway route keeps the
tolerance. Scoring judges the actual arrival by the same rule and margin.

There is no assessment (`late`/`lateProbability` null) for frequency-based
trips (no clock-time arrival; they still get a range), for the first stop of
a trip (stop path 0), for a stop the schedule gives no time for (a route with
neither a schedule nor a headway to judge by), and for every algorithm other
than `SMILE_GBM`.

### 9.5 Configuration

`transittrack.predict.smile.*` and `transittrack.predict.late.*`, all listed
in §2 and in [configuration.md §6](configuration.md#6-prediction-generation--transittrackpredict).
The algorithm itself is enabled per feed by listing `smile_gbm` in
`prediction-algorithms`.

### 9.6 GraphQL

- `StopPrediction.rangeLowSec`/`rangeHighSec`, `late`/`lateProbability` on
  `vehiclePredictions` and `stopPredictions` rows.
- `tripLateness(feedCode, vehicleId, algorithm)` — the assessment at the
  vehicle's next stop, next timepoint and terminal. Without `algorithm` the
  feed's algorithms are tried in order: the first whose open predictions carry
  a late assessment, else the first with open predictions at all (its late
  fields are then null).
- `PredictionAccuracySummary`/`PredictionAccuracyHorizon.rangeCoverage` and
  `rangeSampleCount` (§5).

```graphql
{
  tripLateness(feedCode: "stpt", vehicleId: "1234") {
    algorithm
    nextStop { stop { stopId } predictedArrival scheduledArrival rangeLowSec rangeHighSec late lateProbability }
    terminal { late lateProbability }
  }
  predictionAccuracy(feedCode: "stpt") {
    algorithm pairedSampleCount meanAbsErrorSec rangeCoverage rangeSampleCount
    byHorizon { horizonSec meanAbsErrorSec rangeCoverage }
  }
}
```

### 9.7 From shadow to live

Every listed algorithm predicts and is scored, but only the first is served,
so a new algorithm is introduced in **shadow**:

1. **Shadow.** Add `smile_gbm` **after** the algorithm the feed currently
   serves, e.g. `prediction-algorithms: [ kalman, schedule_adherence, smile_gbm ]`
   (the shipped `stpt` configuration). Stop boards and `headway` keep
   serving `kalman`. (`tripLateness` without an `algorithm` already uses
   `SMILE_GBM` once it predicts, being the only algorithm with late
   assessments, §9.6.) Wait for the first nightly training (§9.2), then for
   scored crossings.
2. **Compare.** `predictionAccuracy` per algorithm and per horizon
   (`byHorizon`): MAE, signed error and P90/P95 over paired cohorts, plus
   `rangeCoverage` for `SMILE_GBM` (near 0.8 is healthy). Paired statistics
   need every compared algorithm to have a forecast in the cohort, so until
   the first model exists the default comparison has no paired samples; query
   one `algorithm` at a time then. Context filters (`routeId`, `dayType`,
   `timeBand`, …) show where it helps.
3. **Serve.** Move `smile_gbm` to the **front** of the feed's
   `prediction-algorithms`: `stopPredictions` and `headway` then use it by
   default. **Reverting** is moving it back.

**Stop-board fallback.** Without an explicit `algorithm`, `stopPredictions`
(and so `headway`) try the feed's algorithms in order and use the first that
has live predictions for the stop — for the whole board, never mixed per
vehicle. A feed serving `smile_gbm` before its first model (or with a model
that can't cover a stop) therefore shows the next listed algorithm's board
instead of an empty one. An explicit `algorithm` is never substituted.

### 9.8 Licensing

Smile (`com.github.haifengl:smile-core`) is licensed under the **GPL-3.0**.
Linking it means the project must be distributed under GPL-3.0-compatible
terms. A `LICENSE` file is not yet present in this repository; one has to be
added before the project is distributed.

---

## 10. Known limitations

1. **Learned state resets when a pattern changes** — a new route, shape or stop
   list gives the pattern a new `pattern_key`, and its learning starts again
   from the schedule seed.
2. **Stop passes between two reports are interpolated** at uniform
   speed. Samples between stops passed in the same interval are dropped (§3),
   and excessive gaps are excluded, but accepted endpoints remain approximate.
   Empirical uncertainty calibration and correlated-leg modeling are not yet
   implemented.
3. **Outlier clipping biases skewed travel times slightly low**: the long
   tail of genuinely slow trips is clipped together with true outliers.
4. **A frequency trip's next lap may require a matching gap/reassignment.**
   The AVL matchers never match a vehicle backwards along the trip it is
   already on, so a vehicle looping straight back to the start of the same
   frequency trip stays matched near the end until it is unmatched or matched
   to another trip; only then does the new lap reset (§4).

Deferred entirely (see the design spec, §1/§12): emitting GTFS-RT `TripUpdate`
from these predictions, and a materialized headway snapshot table (only
needed if the live aggregation in §6 proves too slow at scale).

See also: [docs/avl.md](avl.md), [docs/gtfs.md](gtfs.md), [docs/schedule.md](schedule.md).
