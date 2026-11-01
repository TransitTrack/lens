# Pipeline benchmarks

Load benchmarks for the three stages of the AVL pipeline: **ingest** (decode a GTFS-RT payload
and store it), **match/process** (assign pending reports to trips), and **predict** (generate
`vehicle_prediction` rows from matched reports). They live in their own Gradle source set
(`src/benchmark/kotlin`), built with [kotlinx-benchmark](https://github.com/Kotlin/kotlinx-benchmark)
on top of JMH, and are a separate, opt-in task from `./gradlew test` - they boot a real Spring
context against a Testcontainers Postgres instance and run for minutes, not seconds.

## Running

```sh
./gradlew benchmark        # full suite: both vehicleCount values, 1 warmup + 3 measurement iterations
./gradlew smokeBenchmark   # fast sanity check: AvlIngestBenchmark only, vehicleCount=400, 1 iteration
```

Each `(benchmark class, vehicleCount)` combination runs in its own forked JVM (`@Fork(1)`), so
`./gradlew benchmark` boots six independent Postgres containers + Spring contexts in sequence.
Expect the full run to take a couple of minutes; `smokeBenchmark` finishes in well under a minute
and is the one to reach for while iterating on the benchmark code itself.

Results print as a summary table at the end (`ms/op`, lower is better) and are also written to
`build/reports/benchmarks/<configuration>/<timestamp>/benchmark.json` (the configured
`reportFormat`; JSON by default - kotlinx-benchmark doesn't have a built-in HTML format).

For an HTML view, either drop that `benchmark.json` onto https://jmh.morethan.io/ (it's the raw
JMH JSON format, parsed entirely client-side - nothing is uploaded), or run:

```sh
./gradlew benchmarkReport
```

which renders the most recently modified `benchmark.json` as a self-contained HTML page at
`build/reports/benchmarks/html/index.html` - open it directly in a browser, no server needed.

To run a single class, add a Gradle `include(...)` filter to a configuration in `build.gradle.kts`
(see the `smoke` profile), or pass `-Pbenchmarks_include=<regex>` if your kotlinx-benchmark version
supports it - check `./gradlew help --task benchmark` for the exact flags available.

## In CI

`.github/workflows/benchmark.yml` runs `smokeBenchmark` then the full suite on every push to
`main` and every same-repo PR, and hands the raw JMH JSON to
[kitlangton/jmh-benchmark-action](https://github.com/kitlangton/jmh-benchmark-action): on a PR it
comments a before/after comparison against `main`'s saved baseline; on a push to `main` it updates
that baseline. See [docs/ci.md](ci.md) for the full pipeline and its permission requirements.

## What's measured

| Class | Method under measurement | Isolates |
|---|---|---|
| `AvlIngestBenchmark` | `AvlIngestService.pollOnce` | decode a GTFS-RT `FeedMessage`, drop stale/duplicate reports, batch-insert as PENDING `avl_report` rows |
| `AvlMatchBenchmark` | `AvlMatchProcessor.processBatch` | claim PENDING `avl_report` rows, run `DescriptorThenInferMatcher`, write `vehicle_match`/`vehicle_state` |
| `PredictionBenchmark` | `PredictionProcessor.processBatch` | claim PENDING `vehicle_match` rows, detect stop-path crossings, learn travel times, generate `vehicle_prediction` rows |

Each class isolates its own stage: `AvlMatchBenchmark` seeds `avl_report` rows directly (bypassing
decode), and `PredictionBenchmark` seeds `vehicle_match` rows by running ingest+match as unmeasured
`@Setup` work first. `Mode.SingleShotTime` is used throughout with `@Setup(Level.Invocation)`
reseeding fresh data before every rep - each measured call pays real decode/insert/match/predict
cost rather than hitting a warm dedup fast path on a repeat.

`vehicleCount` covers two scales:
- **400** - a realistic single-feed poll, close to what `poznan`/`wroclaw` looked like in
  production per the DB stats gathered investigating this session's performance issues.
- **4000** - a stress case, well past `avl.match.claimBatchSize` / `predict.run.claimBatchSize`
  (500 each), to catch scaling problems before they show up in production.

## The synthetic fixture

`BenchmarkGtfsFixture` generates a small GTFS static schedule in memory (3 routes × 4 trips each,
a 15-point shape per route, 5 stops) rather than shipping fixture files on disk. Its calendar
covers every day of the week from 2020 to 2035, deliberately unlike a real feed - the `poznan`
AVL-matching investigation this session found a feed whose active revision's calendar didn't
cover "today" at all, which is exactly the kind of accident a benchmark fixture shouldn't
reproduce.

Synthetic AVL reports are split three ways across vehicles (`PipelineBenchmarkEnvironment.
descriptorMode`): a third carry a resolvable `trip_id` (the `TrustDescriptorMatcher` fast path), a
third carry only a `route_id` (forcing route+time disambiguation), and a third carry neither
(forcing a full `FullInferenceMatcher` fallback) - reflecting the descriptor-reliability spread
actually observed across `poznan`/`stpt`/`wroclaw` in production.

## Why a hand-rolled Spring bootstrap

`PipelineBenchmarkEnvironment` builds its own `SpringApplicationBuilder` rather than reusing
`@SpringBootTest`, because JMH's generated harness runs outside JUnit entirely. A few non-obvious
things it has to handle:

- **Config-seeded feeds leak in anyway.** `transittrack.feed.feeds` is a YAML-bound
  `List<FeedDef>`; a scalar property override (`"transittrack.feed.feeds" to ""`) does not
  override an indexed list from a lower-priority source, so `GtfsFeedConfigSynchronizer` still
  seeds the real `poznan`/`stpt`/`otl`/`wroclaw` feeds from `application.yaml` at startup. The
  environment deletes `avl_feed`/`gtfs_feed` right after context boot to clear them before
  creating its own feed. `GtfsFeedConfigSynchronizer` kicks off each of those feeds' ingest on a
  background thread before returning control, so this races that thread - expect (harmless)
  `NoSuchElementException` stack traces logged from `IngestionService`/`RevisionService` on every
  run; they're confined to that background thread and don't affect any measured result.
- **A property-only "make the interval huge" approach doesn't stop the first background poll.**
  Spring's `scheduleWithFixedDelay(task, Duration)` always runs its first execution immediately
  regardless of the delay value, so bumping `avl.match.match-interval-ms`/`predict.run.interval-ms`
  only suppresses *repeat* executions. `AvlPoller`/`AvlMatchProcessor`/`PredictionProcessor`'s
  per-feed background loop only starts once their `FeedLockCoordinator` grants them ownership of a
  feed id, so the environment pre-claims that same ShedLock row for its feed
  (`poisonBackgroundSchedulerLocks`) - `processBatch()` itself doesn't check ownership, only the
  scheduled per-feed loop does, so this blocks exactly the interference and nothing else.
- **`@Setup(Level.Trial)` can run more than once for the same state instance.** JMH has been
  observed to invoke it twice; `PipelineBenchmarkEnvironment.start()` is idempotent (an early
  `started` guard) and the feed-table cleanup above also makes a second invocation safe rather than
  failing on a duplicate feed code.

## GTFS validate + parse: MobilityData vs gtfs-analyzer (2026-10-31, one-off)

A one-off measurement taken when `org.mobilitydata.gtfs-validator` was replaced by gtfs-analyzer
(validation) and the in-house streaming reader (parsing). Not part of the JMH suite. The harness
validated the archive and mapped every CSV/shapes row to its entity in memory, with no database
writes, inside the Gradle test JVM (`maxHeapSize = 2g`, Apple Silicon). Feed: Timișoara, 1.8 MB
zipped / 12.1 MB unzipped (`stop_times.txt` 8.9 MB), 264,582 rows.

| Pipeline | Cold run | Warm runs (best) | Peak Java heap (warm) |
| --- | --- | --- | --- |
| MobilityData validator + `GtfsFeedContainer` | 1305 ms | 452 ms | 266–399 MB |
| gtfs-analyzer + `GtfsZip`/`GtfsCsv` streaming | 849 ms | 364 ms | 104–108 MB |

Peak heap is the sum of the heap pools' peak usage after a forced GC and reset. It does not include
the Rust engine's native allocations, so the analyzer's real footprint is somewhat higher than the
heap figure. Both pipelines produced the same row count.

## Exposed migration baseline

Pre-migration numbers (JPA/Hibernate + JdbcTemplate persistence), recorded so the Exposed port can
be compared like for like. Commit: `4c6c8f6` (`main`, branch `feature/exposed-migration` before any
persistence change). Run with `./gradlew baselineBenchmark` on a local macOS dev machine; re-run the
same task after the port and compare.

**Settings:** 2 warmup iterations, 5 measurement iterations, 2 JVM forks (so 10 samples per
benchmark), JMH `gc` profiler, all three benchmark classes, `vehicleCount` in {400, 4000},
`Mode.SingleShotTime`, ms/op.

**kotlinx-benchmark 0.5.0 deviation:** the plugin's `advanced(...)` supports `jvmForks` but has no
`jvmProfiler` option, and the `@Fork(1)` annotations are overridden by `jvmForks`. To get the `gc`
profiler (`gc.alloc.rate.norm`), `build.gradle.kts` registers the `baseline` configuration
(warmups/iterations/`jvmForks = 2`) and then re-points the generated `benchmarkBaselineBenchmark`
task at JMH's own `org.openjdk.jmh.Main` with `-f 2 -wi 2 -i 5 -prof gc -rf json`. Use
`-PbaselineInclude=<regex>` to run a subset. Output:
`build/reports/benchmarks/baseline/<timestamp>/benchmark.json`.

| Benchmark | vehicleCount | Score (ms/op) | gc.alloc.rate.norm (MiB/op) |
|---|---|---|---|
| AvlIngestBenchmark.ingest | 400 | 43.7 ± 7.8 | 3.75 ± 1.28 |
| AvlIngestBenchmark.ingest | 4000 | 352.8 ± 15.8 | 32.85 ± 1.26 |
| AvlMatchBenchmark.match | 400 | 85.5 ± 9.5 | 22.46 ± 1.04 |
| AvlMatchBenchmark.match | 4000 | 176.4 ± 14.1 | 72.22 ± 1.07 |
| PredictionBenchmark.predict | 400 | 123.8 ± 19.1 | 32.47 ± 1.00 |
| PredictionBenchmark.predict | 4000 | 285.0 ± 88.7 | 96.72 ± 1.02 |

(Score ± error is the JMH 99.9% confidence interval over the 10 samples.)

### Live app steady state (informational)

Local profile against `docker compose` Postgres, polling the `stpt` feed (plus the other
configured local feeds), `/actuator/prometheus` sampled every 60 s for 10 minutes starting about 90 s
after boot. `jvm_gc_live_data_size_bytes` is live heap after GC; `process_cpu_usage` is the JVM's
CPU fraction. The `jvm_memory_used_bytes{area="heap"}` sum was not captured (label-order mistake
in the sampling script), so only post-GC live data and CPU are recorded.

| Time | Live data after GC (MiB) | process_cpu_usage |
|---|---|---|
| 23:24:02 | 208.3 | 0.0009 |
| 23:25:03 | 215.2 | 0.0014 |
| 23:26:03 | 215.2 | 0.0014 |
| 23:27:03 | 215.2 | 0.0012 |
| 23:28:03 | 193.7 | 0.0009 |
| 23:29:03 | 193.7 | 0.0010 |
| 23:30:03 | 193.7 | 0.0009 |
| 23:31:03 | 193.7 | 0.0009 |
| 23:32:03 | 193.7 | 0.0007 |
| 23:33:03 | 144.9 | 0.0008 |

The live-data gauge only updates on a major GC, hence the repeated values.

## Exposed migration: after

Post-migration numbers, recorded to check the Exposed port against the baseline above. Commit:
`f46894b` (`feature/exposed-migration`, Exposed 1.6.0, with the three fixes listed below). Same task
and settings as the baseline: `./gradlew baselineBenchmark`, 2 warmups, 5 iterations, 2 forks,
`-prof gc`.

**Environment:** Apple M4 Pro (14 cores, 48 GB), macOS 27.2, Temurin JDK 25 (`25+36-LTS`),
Testcontainers `postgres:18-alpine` on OrbStack (Docker 29.8.2). All runs used isolated `git worktree`
checkouts: `f46894b` for "after" and `d97f6e5` for the baseline re-run. `d97f6e5` has the same
application code as `4c6c8f6` and only adds the `baselineBenchmark` task.

**Same-session baseline.** The fixture's trips run at 00:00, 06:00, 12:00 and 18:00 UTC. How much
matching and prediction work a run does therefore depends on the wall-clock time it runs at. On one
afternoon the same post-migration code measured both 634 and 149 ms/op for `predict`/400, half an
hour apart. Each "after" run was therefore paired with a run of the pre-migration code
in the same session, minutes apart. Verdicts are against that paired run. The
2026-10-31 numbers are kept for reference. A verdict of regressed or improved means the means differ by
more than the sum of both error margins (99.9% CI). Times are in ms/op and allocation
(`gc.alloc.rate.norm`) is in MiB/op. Both are lower-is-better.

| Benchmark | vehicleCount | Baseline 2026-10-31 (time · alloc) | Baseline re-run, paired (time · alloc) | After `f46894b` (time · alloc) | Time | Alloc |
|---|---|---|---|---|---|---|
| AvlIngestBenchmark.ingest | 400 | 43.7 ± 7.8 · 3.75 ± 1.28 | 44.3 ± 13.3 · 3.76 ± 1.29 | 14.9 ± 2.7 · 4.44 ± 0.70 | improved | parity |
| AvlIngestBenchmark.ingest | 4000 | 352.8 ± 15.8 · 32.85 ± 1.26 | 357.2 ± 15.8 · 32.84 ± 1.27 | 73.5 ± 5.5 · 37.59 ± 0.95 | improved | **regressed** (+14%) |
| AvlMatchBenchmark.match | 400 | 85.5 ± 9.5 · 22.46 ± 1.04 | 84.2 ± 12.4 · 22.54 ± 1.09 | 54.4 ± 8.5 · 17.28 ± 0.45 | improved | improved |
| AvlMatchBenchmark.match | 4000 | 176.4 ± 14.1 · 72.22 ± 1.07 | 179.0 ± 11.6 · 72.31 ± 1.22 | 87.4 ± 17.5 · 63.08 ± 1.38 | improved | improved |
| PredictionBenchmark.predict | 400 | 123.8 ± 19.1 · 32.47 ± 1.00 | 185.7 ± 40.9 · 33.12 ± 1.04 * | 184.5 ± 27.2 · 39.59 ± 0.98 * | parity | **regressed** (+20%) |
| PredictionBenchmark.predict | 4000 | 285.0 ± 88.7 · 96.72 ± 1.02 | 445.7 ± 177.3 · 98.37 ± 1.00 * | 425.7 ± 54.8 · 115.35 ± 1.31 * | parity | **regressed** (+17%) |

\* The prediction rows come from an interleaved re-run of `PredictionBenchmark` alone (after, base,
after, starting 17:03 UTC). In the full paired run two minutes earlier, `predict` read 137.2 ± 18.2 → 190.6 ± 28.9
(400) and 282.8 ± 28.3 → 425.3 ± 54.9 (4000) ms/op. That looked like a time regression, but the
interleaved run put the pre-migration code at 185.7 and 445.7 ms/op as well. The
second "after" run in the interleave read 217.0 ± 47.9 and 436.6 ± 54.8 ms/op, again parity.
Allocation was stable across all of these runs.

The port as first measured (`ea265c7`, paired run, 2026-11-01 13:10 UTC) had times at parity or
better everywhere, but every allocation figure had regressed: ingest 8.88 / 87.90, match 27.08 / 115.35,
predict 55.95 / 185.84 MiB/op (400 / 4000). The fixes, each measured on the whole suite
(time stayed at parity in each step):

- `57a8de9` perf(db): the exception-translation aspect was ordered ahead of Spring's
  `ExposeInvocationInterceptor`. Every `@Transactional` call created and swallowed an
  `IllegalStateException` while matching the pointcut (JFR: about 2,300 throwables/s in `predict`,
  against about 5/s before the port). Time and allocation stayed within noise, apart from a small
  allocation drop at ingest/4000 (87.90 → 85.13 MiB/op).
- `96c39c3` perf(db): Exposed's `batchInsert`/`batchUpsert` build a `ResultRow` for every written row
  even with `shouldReturnGeneratedValues = false`, and walk the arguments three times (JFR TLAB
  profile: `InsertBlockingExecutable.processResults` was about 38% of ingest's allocation). The AVL and
  prediction writers now use `batchInsertWithoutResults`/`batchUpsertWithoutResults`
  (`db/BatchWrites.kt`). Results at 400 / 4000: ingest 8.77 / 85.13 → 4.45 / 38.69, match
  26.64 / 119.93 → 17.04 / 61.60, predict 55.73 / 192.03 → 44.82 / 128.81 MiB/op.
- `f46894b` perf(db): `SameSqlBatch` (`execBatch`) rendered every statement's SQL to compare them,
  which was the largest allocation in `PredictionWriter.insertCheckpoints`. It now renders the first
  statement only and checks the parameter types. Predict 400 / 4000: 44.82 / 128.81 → 39.71 / 115.16 MiB/op.

**Still regressed (allocation only):** ingest/4000 by about 5 MiB/op, predict by about 6 MiB/op (400)
and 17 MiB/op (4000). A JFR TLAB profile puts what remains in Exposed's per-statement model rather than
in anything this codebase does twice:
- ingest: `BatchInsertStatement` copies a map per row (`addBatch`, `valuesAndClientDefaults`, the
  argument pairs and a `QueryBuilder` per row).
- predict: `insertCheckpoints` builds and renders a full insert-select DSL tree per candidate, where
  the old code reused one SQL string. `findById` and `findPreviousForVehicle` render their SQL twice per
  call (once for the text, once to collect the arguments) and build a `ResultRow` map per row.

Closing those gaps would need statement-level SQL caching or binding rows past Exposed's statement
classes. That goes beyond fixing what the port duplicated, so it was not done here.

### Live app steady state (informational)

Same procedure as the baseline: `bootRun` with the `local` profile at `f46894b`, against the
`docker compose` Postgres, polling `stpt` and the other configured local feeds. `/actuator/prometheus`
was sampled every 60 s for 10 minutes starting about 90 s after boot. Heap used is the sum of
`jvm_memory_used_bytes{area="heap"}` over the heap pools. The baseline script missed that figure.
The heap-used sum was lost again in this run: the pools' `id` label contains spaces (`"G1 Eden
Space"`), so the sampling script parsed the wrong field. Live data after GC and CPU are recorded,
the same two figures as the baseline. `stpt` was matching about 75-79 vehicles per run while the
samples were taken (2026-11-01, about 19:07-19:17 local time; the baseline was sampled at 23:24-23:33,
so the live load differs).

| Time | Live data after GC (MiB) | process_cpu_usage |
|---|---|---|
| 19:07:30 | 131.2 | 0.0003 |
| 19:08:31 | 141.8 | 0.0004 |
| 19:09:31 | 111.0 | 0.0003 |
| 19:10:31 | 103.3 | 0.0237 |
| 19:11:31 | 103.3 | 0.0014 |
| 19:12:31 | 101.4 | 0.0005 |
| 19:13:31 | 70.2 | 0.0006 |
| 19:14:31 | 70.2 | 0.0003 |
| 19:15:31 | 71.5 | 0.0004 |
| 19:16:31 | 69.2 | 0.0003 |

Live data after GC settled at about 70 MiB, against 145-215 MiB before the port. Mean CPU across the
samples was about 0.003, against about 0.001 at baseline. Without the 19:10 sample, which landed on
a silent-vehicle sweep and a match run, the mean is about 0.0005. The load differed and each side is
a single short run, so these figures are a rough indication, not a measurement.
