# transittrack extension-api

Interfaces for extending transittrack without forking or rebuilding core:
custom AVL feed decoders, GTFS validation rules, vehicle-matching
strategies, and prediction algorithms. This module depends on nothing from
core — only the Kotlin stdlib plus GeoTools/JTS (`api` dependencies, see
below) — so an extension jar built against it never needs core on its own
compile classpath.

**Geometry dependency:** this module's geometry types (`Coordinate`,
`ProjectedLine`, `geodeticDistanceM`/`geodeticBearingDegrees` — see
[Coordinates and geometry](#coordinates-and-geometry) below) are backed by
[GeoTools](https://geotools.org/) and [JTS](https://locationtech.github.io/jts/),
both declared as `api` dependencies so they're transitively visible to
anything compiling against `extension-api`. GeoTools isn't published to
Maven Central, so any extension's own build must add the OSGeo repository:

```kotlin
repositories {
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
}
```

Core is AGPL-3.0-licensed; this module is intended to carry a separate,
permissive license of its own (not yet finalized/added as a `LICENSE`
file), so building a closed-source extension against these interfaces
isn't intended to itself require releasing that extension's source. Core
is still consumed as a library at runtime (the packaged transittrack
image), so this is a packaging/licensing boundary, not a substitute for
your own legal review before relying on it commercially. Note that
GeoTools itself is LGPL-2.1-licensed — a weaker copyleft than core's
AGPL-3.0, but still a term to account for in that same legal review,
since it's now an `api` dependency of this module rather than core alone.

**Loading:** extension jars sit on the running app's classpath — see
[docs/deployment.md §8](../docs/deployment.md#8-extensions) for the
`loader.path`/`LOADER_PATH` and Helm `extraVolumes` wiring, and
[examples/example-extension/](../examples/example-extension/) for a
minimal, buildable extension exercising all of this end to end.

**Stability:** all four interfaces below may gain new methods in a minor
release (implement against an interface, and a later minor version may add
a method you need to implement too — watch the changelog). No existing
method's signature changes without a major version bump — except the
breaking release noted in [Changelog / migration notes](#changelog--migration-notes)
below, so extension authors pinning a version should check that section
before upgrading across it.

## Coordinates and geometry

**Every `Coordinate` in this API is `Coordinate(x = longitude, y = latitude)`
— the reverse of a natural "(lat, lon)" reading order.** This is
[`org.locationtech.jts.geom.Coordinate`](https://locationtech.github.io/jts/),
matching GeoTools' own `(longitude, latitude)` argument order, and it is the
single easiest mistake to make against this API: swapping the two
components silently produces a coordinate on the wrong side of the planet
rather than a compile error. Public members typed as (or returning)
`Coordinate` include:

- `AvlReportView.point` — `Coordinate(lon, lat)` built from the view's own
  `lat`/`lon` fields.
- `MatchContext.patternExtentWithin(tripPatternId, p: Coordinate, distanceM)`
  — `p` follows the same `(lon, lat)` convention.
- `MatchOutcome.Matched.snapped` — the matched position snapped onto the
  pattern's shape.
- `PatternGeometry.endOfPath(i)` and `PatternGeometry.line` (a
  `ProjectedLine`, see below) — the pattern's shape geometry.

`eu.transittrack.geo.ProjectedLine` (`eu.transittrack.geo.ProjectedLine.kt`)
wraps a `List<Coordinate>` as a WGS-84 polyline supporting projection
(`project(p: Coordinate): LineProjection`, giving `distanceAlong`/
`deviationM` in metres) and point-at-distance lookups (`pointAt(d): Coordinate`).
`eu.transittrack.geo.geodeticDistanceM`/`geodeticBearingDegrees`
(`eu.transittrack.geo.Geodesy.kt`) compute ellipsoidal (WGS-84) distance and
initial bearing between two `Coordinate`s directly, via GeoTools'
`GeodeticCalculator`. See that file's own KDoc for the full convention
note.

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
need them directly: `org.locationtech.jts.geom.Coordinate`,
`eu.transittrack.geo.ProjectedLine`,
`eu.transittrack.geo.geodeticDistanceM`/`geodeticBearingDegrees` (see
[Coordinates and geometry](#coordinates-and-geometry) above), `AvlFormat`,
`AvlAssignmentMode` (`eu.transittrack.AvlEnums`), `PredictionAlgorithm`,
`PredictionMode` (`eu.transittrack.predict.PredictionEnums`).

## Changelog / migration notes

**Breaking release — JTS/GeoTools geometry migration:** this module's
hand-rolled `Point`, `Vector`, `Polyline`, and `Projection` types
(`eu.transittrack.Geo`) have been removed entirely and replaced by
`org.locationtech.jts.geom.Coordinate` and
`eu.transittrack.geo.ProjectedLine`, with distance/bearing math moved to
`eu.transittrack.geo.geodeticDistanceM`/`geodeticBearingDegrees` (GeoTools-
backed, ellipsoidal WGS-84, replacing the old spherical/haversine math).
Every public signature that touched the old types now takes/returns the
new ones instead — this is exactly the kind of signature change the
stability promise above says won't happen without a major version bump.
Extension authors on a version pin predating this release should treat
upgrading across it as a breaking change: recompile, fix any `Point`/
`Vector`/`Polyline` usage against the new types, and pay close attention
to axis order while doing it — the old `Point(lat, lon)` took latitude
first, but the new `Coordinate(x, y)` takes **longitude first**
(`x = longitude, y = latitude`, see
[Coordinates and geometry](#coordinates-and-geometry) above). A
mechanical field-for-field port (`Point(a, b)` → `Coordinate(a, b)`)
silently swaps the two axes; it must become `Coordinate(b, a)`. The API
surface itself also differs (`pointAt`/`project` on `ProjectedLine`
instead of methods on `Polyline`/`Vector`).
