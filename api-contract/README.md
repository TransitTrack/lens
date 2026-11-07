# TransitTrack REST contract

`openapi.yaml` is the versioned contract of the REST API (server base `/api/v1`). The server
interfaces and the browser client are generated from it; the server implements every operation.

## Resources are flat

A response carries the resource's own fields and the **ids** of what it relates to. There is no
field selection and no `include`: to follow a relationship, read the related resource through its own
endpoint, passing the same `revisionId` when the response gave one.

| Start from | Follow with |
| --- | --- |
| `Trip.routeId`, `.shapeId`, `.patternKey`, `.blockId` + `.serviceId` | `routes/{id}`, `shapes/{id}`, `trip-patterns/{key}`, `blocks/{blockId}?serviceId=` |
| `Trip` → its stops and times | `trips/{tripId}/stop-times`, `schedule-times?tripId=` |
| `TripPattern` → stops and trips | `stop-paths?patternKey=`, `trips?patternKey=` |
| `StopPath.stopId`, `StopTime.stopId`, `StopPrediction.stopId`, `Vehicle.currentStopId` | `stops/{id}` |
| `Block` → trips | `block-trips?blockId=&serviceId=` (ordered, with layover / deadhead), `trips?blockId=&serviceId=` |
| `Route` → trips and patterns | `trips?routeId=`, `trip-patterns?routeId=` |
| `Stop` → child stops | `stops?parentStation=` |
| `Feed` → revisions | `feeds/{code}/revisions` (`?status=ACTIVE` for the live one) |
| `Vehicle.revisionId` + `.tripId`, `AvlReportMatch.revisionId` + `.tripId` | `trips/{tripId}?revisionId=` on the vehicle's GTFS feed (`AvlFeed.gtfsFeedCode`) |

Embedded value objects (a report's `match`, positions, extents) stay inline.

## Conventions

- Collection operations take `offset` and `limit` (default 0 and 100; maximum 500), return an
  array and set `X-Total-Count`; follow pages until all rows are read. Offset paging is not a
  stable snapshot while live data changes. Operations with their own limit / page arguments keep
  them.
- `feedGeometry` returns GeoJSON and relies on HTTP compression negotiation. The geometry embedded in
  patterns and shapes is a GeoJSON `LineString`, gzip-compressed then base64-encoded.
- Unknown resources are `404`, invalid input and unusable state `400`, name / version conflicts
  `409`. Draft version and editor-lock conflicts are `409` problems carrying `code`
  (`STALE_DRAFT`, `LOCK_LOST`). Errors are `application/problem+json`.
- `Long` is `int64`; JavaScript clients must take care beyond the safe integer range.
- `/api/feeds/{feedCode}/vehicles` and `/api/feeds/{feedCode}/geometry` are the map's own bounded
  endpoints (all vehicles in one response with just the fields the map draws); they are not part of
  this contract.

## Consistency check

Run `python3 api-contract/validate.py` from the repository root (requires PyYAML). It checks YAML
parsing, local references, path parameters, unique operation ids and parameters. It is a focused
consistency check, not a complete OpenAPI 3.1 validator or a conformance test; `:app:openApiValidate`
runs as part of `:app:check`.

## Generated code

The `:app` Gradle module uses OpenAPI Generator's `kotlin-spring` generator (Spring Boot 4,
interface-only). Generated Kotlin is written to `app/build/generated/openapi` and compiled with the
application; do not edit or commit it.

```sh
./gradlew :app:openApiValidate :app:openApiGenerate
./gradlew :app:compileKotlin
./gradlew :app:generateRestClient   # TypeScript fetch client into web/generated/rest
```

Controllers map domain rows straight to the generated models (`RestEntityMapper`). Some services
still load a collection and page it in memory, so benchmark representative feeds and push paging into
the queries before relying on `limit` to bound server work.
