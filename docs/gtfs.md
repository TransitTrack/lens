# GTFS Schedule Ingestion & Versioned Storage

This subsystem (`eu.transittrack.gtfs`) ingests GTFS Schedule ("static") feeds
from remote URLs, stores the full GTFS Schedule spec in PostgreSQL, and keeps
every import as an immutable **revision**. A Spring GraphQL read API serves the
active revision of each feed.

Design reference:
[`docs/superpowers/specs/2026-08-30-gtfs-ingestion-versioned-storage-design.md`](superpowers/specs/2026-08-30-gtfs-ingestion-versioned-storage-design.md).

Out of scope: GTFS-Realtime, trip planning / routing.

---

## 1. Defining a feed

A feed is a `{ code, name, description?, url, pollingCron?, enabled?, autoActivate? }`
record. `url` is the only transport detail — everything else is metadata or
policy. There are two ways to create one, and both ingest identically.

### 1a. Configuration (`transittrack.gtfs.feeds`)

Declared feeds are reconciled into the `gtfs_feed` table at startup by
`GtfsFeedConfigSynchronizer` (an `ApplicationRunner` that runs after Liquibase),
with `source = CONFIG`:

| Situation | Effect |
| --- | --- |
| `code` not in DB | insert |
| existing `CONFIG` feed | update `name`, `description`, `url`, `pollingCron`, `enabled`, `autoActivate` |
| existing `API` feed, same `code` | **startup fails** — ambiguous ownership |
| `CONFIG` feed in DB, absent from config | left untouched by default; deleted only when `transittrack.gtfs.prune-config-feeds: true` |

```yaml
transittrack:
  gtfs:
    feeds:
      - code: wroclaw
        name: "Wrocław"
        description: "MPK Wrocław"          # optional
        url: "https://przystanek.wroclaw.pl/feed/gtfs.zip"
        polling-cron: "0 30 3 * * *"        # optional; per-feed schedule
        enabled: true                       # optional, default true
        auto-activate: true                 # optional; overrides ingest.auto-activate
```

The list may be empty — feeds can then be created purely via the mutation below.

### 1b. GraphQL mutation (`registerGtfsFeed`)

Creates a feed with `source = API`:

```graphql
mutation {
  registerGtfsFeed(input: {
    code: "wroclaw"
    name: "Wrocław"
    url: "https://przystanek.wroclaw.pl/feed/gtfs.zip"
    pollingCron: "0 30 3 * * *"
    enabled: true
    autoActivate: true
  }) { code source enabled }
}
```

Feed CRUD:

| Mutation | Notes |
| --- | --- |
| `registerGtfsFeed(input: RegisterGtfsFeedInput!): GtfsFeed!` | `code`, `name`, `url` required; `description`, `pollingCron`, `enabled`, `autoActivate` optional |
| `updateGtfsFeed(code: String!, input: UpdateGtfsFeedInput!): GtfsFeed!` | `name` and `url` required in the input |
| `deleteGtfsFeed(code: String!): Boolean!` | deletes the feed and its revisions |

---

## 2. Revision lifecycle

Every ingest opens a new `gtfs_revision` row. Revisions are full snapshots — every
GTFS entity row carries the `revision_id` it was loaded under; there is no
in-place mutation of a live revision.

### State machine (spec §5)

```
PENDING -> DOWNLOADING -> PARSING -> VALIDATING -> READY -> ACTIVE -> SUPERSEDED

  any step     -> FAILED     (error_message set, all rows for revision deleted)
  DOWNLOADING  -> UNCHANGED  (SHA-256 == current ACTIVE; stop, no rows written)
  READY -> ACTIVE            swap: previous ACTIVE becomes SUPERSEDED
  ACTIVE -> SUPERSEDED       when a newer revision is activated
```

Terminal: `ACTIVE`, `SUPERSEDED`, `FAILED`, `UNCHANGED`.
Non-terminal: `PENDING`, `DOWNLOADING`, `PARSING`, `VALIDATING`, `READY`
(`READY` is only a resting state when `auto-activate = false`).

### Pipeline steps

1. **Download** — `FeedDownloader` streams the URL to a temp file, capped at
   `download.max-size-bytes`, computing SHA-256 and byte size.
2. **Unchanged check** — if the SHA-256 equals the feed's current `ACTIVE`
   revision's `content_sha256`, the revision is set to `UNCHANGED` and the
   pipeline stops (no rows written).
3. **Unzip & required-file check** — extract the archive to a temp dir. Fails if
   any of `agency.txt`, `stops.txt`, `routes.txt`, `trips.txt`, `stop_times.txt`,
   and (`calendar.txt` or `calendar_dates.txt`) is missing. (`files_present` is
   persisted later, on the `VALIDATING` transition.)
4. **Parse & write** — `GtfsFileRegistry` is iterated in dependency order; each
   present, registered file is streamed by `GtfsCsvReader`, mapped to typed
   entities, and batched (`ingest.batch-size`, default 1000) through
   `RevisionWriter`. `locations.geojson` is read by `GtfsGeoJsonReader`;
   `shapes.txt` also accumulates per-`shape_id` aggregate rows. Files in the
   archive that are not part of the GTFS Schedule spec are ignored (they still
   appear in `filesPresent`). `row_counts` is populated, keyed by physical table
   name (`gtfs_agency`, `gtfs_stop`, …).
5. **Validate** — revision-scoped referential checks (orphan `route_id`,
   `service_id`, `trip_id`, `stop_id`, `shape_id`, `agency_id`,
   `parent_station`, fare/area/network/pathway refs, …). Counts and samples go
   to `validation_report`.
   - `strict-validation = true` → any error → `FAILED`.
   - `strict-validation = false` (default) → errors recorded as a report,
     ingest proceeds.
6. **Derive dates** — `feed_start_date` / `feed_end_date` from `feed_info.txt`
   if present, else min/max over `calendar` and `calendar_dates`.
7. **Ready → Activate** — status becomes `READY`. If the effective
   `auto-activate` (feed override, else `ingest.auto-activate`, default `true`)
   is set, one transaction flips the current `ACTIVE` → `SUPERSEDED` and this
   revision → `ACTIVE`. Otherwise it waits for `activateRevision`.
8. **Prune** — see §4.

Any exception funnels to `FAILED`: `error_message` is set, every row for that
`revision_id` is deleted, and the `ACTIVE` pointer is left untouched. Temp
files are always cleaned up.

### Triggering an ingest

| Trigger | Behaviour |
| --- | --- |
| `ingestFeed(feedCode)` mutation | returns a `PENDING` revision immediately; pipeline runs async on a bounded executor |
| polling scheduler | see §5 |
| `IngestionService.ingestBlocking(feedCode)` | synchronous; returns the finished revision (used by tests / programmatic callers) |

**Guard:** an ingest is refused if the feed already has a revision in an
in-progress state (`PENDING`, `DOWNLOADING`, `PARSING`, `VALIDATING`). A revision
resting at `READY` (auto-activate off) does **not** block a new ingest. At most
one running ingest per feed; different feeds ingest in parallel up to the pool
size.

### Revision mutations

| Mutation | Notes |
| --- | --- |
| `ingestFeed(feedCode: String!): GtfsRevision!` | starts a new ingest, returns the `PENDING` revision |
| `activateRevision(revisionId: ID!): GtfsRevision!` | sets the given revision to `ACTIVE` and demotes the feed's current `ACTIVE` to `SUPERSEDED`. No status precondition is enforced — any revision id is accepted, so callers are responsible for passing a sensible one (normally a `READY` revision). |
| `deleteRevision(revisionId: ID!): Boolean!` | deletes the revision and its rows; **refuses the `ACTIVE` revision** |

---

## 3. `auto-activate` and `strict-validation`

Both are set under `transittrack.gtfs.ingest` and default the same way for every
feed; `auto-activate` can additionally be overridden per feed.

- **`auto-activate`** (default `true`) — when true, a successful ingest is
  promoted to `ACTIVE` automatically (step 7). When false, the ingest stops at
  `READY` and an operator must call `activateRevision` to make it live. Feed-level
  `autoActivate` (config key `auto-activate`, or the mutation input field) wins
  over the global value.
- **`strict-validation`** (default `false`) — when true, any referential
  validation error fails the revision. When false, validation still runs and its
  findings are stored in `validation_report` / exposed as `validationSummary`,
  but the ingest proceeds to activation.

---

## 4. Retention / pruning

After a revision activates, `RevisionService.prune` keeps the `ACTIVE` revision
plus the `transittrack.gtfs.retention.keep-revisions-per-feed` most-recent
terminal revisions (default 5). The rest — and all their rows — are deleted.
`UNCHANGED` and `FAILED` revisions count toward the limit and are pruned first.
The `ACTIVE` revision is never pruned.

```yaml
transittrack:
  gtfs:
    retention:
      keep-revisions-per-feed: 5
```

---

## 5. Polling scheduler

A single sweep (`GtfsIngestScheduler`) runs on
`transittrack.gtfs.polling.sweep-cron` and is only registered when
`transittrack.gtfs.polling.enabled = true`. For each enabled feed that has a
`pollingCron`, it computes whether the feed's cron has fired since the feed's
last ingest attempt and, if so, calls `IngestionService.ingest`. The per-feed
in-progress guard prevents overlap; feeds without a `pollingCron` are never
polled.

```yaml
transittrack:
  gtfs:
    polling:
      enabled: true
      sweep-cron: "0 * * * * *"   # how often the sweep wakes up (6-field Spring cron)
    feeds:
      - code: wroclaw
        name: "Wrocław"
        url: "https://przystanek.wroclaw.pl/feed/gtfs.zip"
        polling-cron: "0 30 3 * * *"   # this feed is due at 03:30 daily
```

---

## 6. Read API

All types are `Gtfs`-prefixed; every entity query resolves against a revision —
optional `revisionId` argument, defaulting to the feed's `ACTIVE` revision (an
error is raised if the feed has none).

### Feed / revision queries

```
gtfsFeeds: [GtfsFeed!]!
gtfsFeed(code: String!): GtfsFeed
gtfsRevisions(feedCode: String!, status: GtfsRevisionStatus): [GtfsRevision!]!
gtfsRevision(id: ID!): GtfsRevision
```

### Typed entity queries

```
gtfsAgencies / gtfsRoutes / gtfsRoute / gtfsStops / gtfsStop /
gtfsTrips / gtfsTrip / gtfsStopTimes / gtfsCalendars / gtfsCalendarDates /
gtfsShape / gtfsFrequencies / gtfsTransfers / gtfsFeedInfo / gtfsPathways / gtfsLevels
```

Nested resolvers: `GtfsRoute.agency`, `GtfsRoute.trips`, `GtfsTrip.route`,
`GtfsTrip.stopTimes`, `GtfsTrip.shape`, `GtfsStopTime.stop`, `GtfsStop.childStops`,
`GtfsStop.level` — all scoped to the parent's `revision_id`.

### Worked example

```graphql
{
  gtfsFeed(code: "wroclaw") {
    source
    activeRevision {
      status
      rowCounts
      validationSummary { errorCount warningCount }
    }
  }
  gtfsStopTimes(feedCode: "wroclaw", tripId: "T1") {
    stopSequence
    arrivalTime
    stop { stopName }
  }
}
```

```json
{
  "gtfsFeed": {
    "source": "CONFIG",
    "activeRevision": {
      "status": "ACTIVE",
      "rowCounts": { "gtfs_agency": 1, "gtfs_stop": 3, "gtfs_route": 1, "gtfs_trip": 1, "gtfs_stop_time": 2 },
      "validationSummary": { "errorCount": 0, "warningCount": 0 }
    }
  },
  "gtfsStopTimes": [
    { "stopSequence": 1, "arrivalTime": "08:00:00", "stop": { "stopName": "First" } },
    { "stopSequence": 2, "arrivalTime": "08:10:00", "stop": { "stopName": "Second" } }
  ]
}
```

### `gtfsRecords` — long-tail escape hatch

The Fares v2 graph, areas/networks, translations, attributions, location groups
and `locations.geojson` are stored and validated but not yet exposed as typed
GraphQL. They are readable as raw JSON rows via:

```
gtfsRecords(feedCode: String!, table: GtfsTable!, revisionId: ID): [JSON!]!
```

`GtfsTable` enum values (GTFS *file* names, plural):

```
FARE_ATTRIBUTES  FARE_RULES  TIMEFRAMES  RIDER_CATEGORIES  FARE_MEDIA  FARE_PRODUCTS
FARE_LEG_RULES  FARE_LEG_JOIN_RULES  FARE_TRANSFER_RULES  AREAS  STOP_AREAS  NETWORKS
ROUTE_NETWORKS  LOCATION_GROUPS  LOCATION_GROUP_STOPS  LOCATIONS  BOOKING_RULES
TRANSLATIONS  ATTRIBUTIONS
```

```graphql
{ gtfsRecords(feedCode: "wroclaw", table: FARE_PRODUCTS) }
```

Typed GraphQL for these entities is a documented follow-up.

---

## 7. Configuration reference

```yaml
transittrack:
  gtfs:
    download:
      connect-timeout-ms: 10000
      read-timeout-ms: 60000
      max-size-bytes: 524288000      # 500 MiB
      user-agent: "transittrack-explorer/0.0.1"
    ingest:
      auto-activate: true
      strict-validation: false
      batch-size: 1000
      temp-dir: ""                   # "" -> java.io.tmpdir
    retention:
      keep-revisions-per-feed: 5
    polling:
      enabled: false
      sweep-cron: "0 0 * * * *"      # hourly
    prune-config-feeds: false        # delete CONFIG feeds no longer in config
    feeds: []                        # see §1a
```
