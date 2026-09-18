# transittrack extension-api

Interfaces for extending transittrack without forking or rebuilding core:
custom AVL feed decoders, GTFS validation rules, vehicle-matching
strategies, and prediction algorithms. This module depends on nothing from
core — only the Kotlin stdlib — so an extension jar built against it never
needs core on its own compile classpath.

Core is AGPL-3.0-licensed; this module is intended to carry a separate,
permissive license of its own (not yet finalized/added as a `LICENSE`
file), so building a closed-source extension against these interfaces
isn't intended to itself require releasing that extension's source. Core
is still consumed as a library at runtime (the packaged transittrack
image), so this is a packaging/licensing boundary, not a substitute for
your own legal review before relying on it commercially.

**Loading:** extension jars sit on the running app's classpath — see
[docs/deployment.md §8](../docs/deployment.md#8-extensions) for the
`loader.path`/`LOADER_PATH` and Helm `extraVolumes` wiring, and
[examples/example-extension/](../examples/example-extension/) for a
minimal, buildable extension exercising all of this end to end.

**Stability:** all four interfaces below may gain new methods in a minor
release (implement against an interface, and a later minor version may add
a method you need to implement too — watch the changelog). No existing
method's signature changes without a major version bump.

## AVL feed decoders

`eu.transittrack.avl.ingest.AvlFeedDecoder` — turns one feed's raw poll
response into `AvlReport`s.

```kotlin
interface AvlFeedDecoder {
    val format: AvlFormat
    fun decode(payload: RawAvlPayload, feed: FeedDescriptor): List<AvlReport>
}
```

- `RawAvlPayload` (`eu.transittrack.avl.feed`) — the bytes/content-type
  fetched from the feed's URL.
- `FeedDescriptor` (`eu.transittrack.avl.ingest`) — the feed's own config
  (`code`, `name`, `url`, `format`, `headers`), independent of any core
  entity.
- `AvlReport` (`eu.transittrack.avl.ingest`) — one vehicle observation:
  position, optional trip/route descriptor fields, occupancy, congestion.

Register a bean implementing this interface (see
`ExampleExtensionAutoConfiguration` for the auto-configuration wiring) and
core's ingest pipeline picks it up for feeds configured with a matching
`format`.

## GTFS validators

`eu.transittrack.gtfs.validate.GtfsValidator` — a custom validation rule
run alongside the canonical MobilityData GTFS validator.

```kotlin
fun interface GtfsValidator {
    fun validate(input: GtfsValidationInput): List<GtfsValidationFinding>
}
```

- `GtfsValidationInput` — `feedCode` plus `gtfsZipPath`, the downloaded
  GTFS archive as-is (the core loader validates straight from the zip, so
  there's no pre-extracted directory to hand over).
- `GtfsValidationFinding` — `severity` (`ERROR`/`WARNING`), `code`,
  `message`, and optional `file`/`entityId`.

Findings are merged into the feed's validation report next to the
canonical validator's own notices; under
`transittrack.ingest.strict-validation`, an `ERROR` finding fails the
ingest the same way a canonical validator error does.

## Vehicle matching strategies

`eu.transittrack.avl.match.VehicleMatcher` — assigns one AVL report to a
scheduled trip.

```kotlin
interface VehicleMatcher {
    val mode: AvlAssignmentMode
    fun match(report: AvlReportView, prev: VehicleStateView?, ctx: MatchContext): MatchOutcome
}
```

- `AvlReportView` / `VehicleStateView` (`eu.transittrack.avl.match`) —
  lean, JPA-free mirrors of the current report and the vehicle's previous
  matched state.
- `MatchContext` (`eu.transittrack.avl.match`) — the per-run, revision-scoped
  read API onto the derived schedule (patterns, geometry, trips, blocks,
  service-day calendars). Core's `AvlMatchContext` implements it directly;
  every method is a cached read, safe to call repeatedly within one match.
- `MatchOutcome` — a sealed result: `Matched` (with the resolved trip,
  block, stop-path index, distance along trip, deviation, schedule
  adherence, snapped position), `Failed`, or `Skipped`.

Bundled matchers (`TrustDescriptorMatcher`, `DescriptorThenInferMatcher`,
`FullInferenceMatcher`, all in core) show the pattern: an extension
registering a `VehicleMatcher` bean with a distinct `mode` makes that mode
selectable per-feed via `transittrack.avl.assignment-mode`.

## Prediction strategies

`eu.transittrack.predict.generate.PredictionStrategy` — projects arrival/
departure predictions for a vehicle's remaining stops.

```kotlin
interface PredictionStrategy {
    val algorithm: PredictionAlgorithm
    fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction>
}
```

- `HorizonStop` — one stop, on one trip, within the vehicle's remaining
  prediction horizon (built by core from the match outcome).
- `GeneratedPrediction` — one strategy's predicted arrival/departure and
  optional confidence, for one stop.

Bundled strategies (`ScheduleAdherenceAlgorithm`,
`HistoricalAverageAlgorithm`, `KalmanAlgorithm`, all in core) show the
pattern: an extension registering a `PredictionStrategy` bean with a
distinct `algorithm` makes that algorithm selectable per-feed via
`transittrack.predict.algorithm`, or run alongside the others under
`transittrack.predict.mode: EVALUATION`.

## Supporting types

Lean, JPA-free mirrors of core's schedule entities, used across the
interfaces above: `TripRef`, `BlockTripRef`, `StopPathRef`
(`eu.transittrack.schedule.model`), `PatternGeometry`, `SchedulePoint`
(`eu.transittrack.avl.match`). Also re-exported here for extensions that
need them directly: `Point`, `Vector`, `Polyline`, `Projection`
(`eu.transittrack.Geo`), `AvlFormat`, `AvlAssignmentMode`
(`eu.transittrack.AvlEnums`), `PredictionAlgorithm`, `PredictionMode`
(`eu.transittrack.predict.PredictionEnums`).
