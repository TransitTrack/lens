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
