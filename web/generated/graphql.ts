/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import type { TypedDocumentNode as DocumentNode } from '@apollo/client';
import { useQuery, useMutation } from '@vue/apollo-composable'
import type { MaybeRefOrGetter } from 'vue'
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  /** Arbitrary JSON value, used where the shape varies (e.g. validation row counts, optimization evidence). */
  JSON: { input: any; output: any; }
  /** 64-bit integer, used for optimistic-concurrency version numbers and other values that can exceed Int's range. */
  Long: { input: number; output: number; }
};

export type AddTripInput = {
  blockId?: InputMaybe<Scalars['String']['input']>;
  directionId?: InputMaybe<Scalars['Int']['input']>;
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  headsign?: InputMaybe<Scalars['String']['input']>;
  routeId: Scalars['String']['input'];
  serviceId: Scalars['String']['input'];
  shapeId?: InputMaybe<Scalars['String']['input']>;
  /** The new trip's scheduled stops, in order. */
  stops: Array<NewStopTimeInput>;
  /** Explicit trip ID, or omit to have one generated. */
  tripId?: InputMaybe<Scalars['String']['input']>;
};

/** GTFS agency.txt: a transit operator. */
export type Agency = {
  __typename?: 'Agency';
  agencyEmail?: Maybe<Scalars['String']['output']>;
  agencyFareUrl?: Maybe<Scalars['String']['output']>;
  agencyId?: Maybe<Scalars['String']['output']>;
  agencyLang?: Maybe<Scalars['String']['output']>;
  agencyName?: Maybe<Scalars['String']['output']>;
  agencyPhone?: Maybe<Scalars['String']['output']>;
  agencyTimezone?: Maybe<Scalars['String']['output']>;
  agencyUrl?: Maybe<Scalars['String']['output']>;
  extent?: Maybe<Extent>;
};

/** How a feed's vehicle-to-trip matching is performed, in increasing order of reliance on inference over the raw feed's own trip descriptors. */
export type AvlAssignmentMode =
  /** Try descriptor-based matching first and fall back to full spatial/temporal inference whenever it fails to produce a match. */
  | 'DESCRIPTOR_THEN_INFER'
  /** Ignore the feed's descriptors entirely; match purely by projecting the vehicle's position against every trip active at that time and picking the best-scoring candidate. */
  | 'FULL_INFERENCE'
  /** Trust the feed's own vehicle descriptors (trip/route/direction/start time) to resolve the trip, falling back to spatial disambiguation only when the descriptor still leaves several trips in play. */
  | 'TRUST_DESCRIPTOR';

/** A configured AVL (vehicle-position) feed: where it's polled from, how its reports are decoded, and how vehicles on it are matched to trips. */
export type AvlFeed = {
  __typename?: 'AvlFeed';
  assignmentMode: AvlAssignmentMode;
  /** Unique identifier for this AVL feed, independent of the GTFS feed it matches against. */
  code: Scalars['String']['output'];
  enabled: Scalars['Boolean']['output'];
  format: AvlFormat;
  /** The gtfs.graphqls Feed.code whose active revision this AVL feed's vehicles are matched against. */
  gtfsFeedCode: Scalars['String']['output'];
  /** ISO-8601 instant of the most recent poll attempt, or null if this feed has never been polled. */
  lastPollAt?: Maybe<Scalars['String']['output']>;
  /** Number of AVL reports ingested on the most recent poll. */
  lastPollReportCount?: Maybe<Scalars['Int']['output']>;
  /** Outcome of the most recent poll attempt (e.g. success or an error description). */
  lastPollStatus?: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
  /** How often this feed is polled, in seconds. */
  pollIntervalSec: Scalars['Int']['output'];
  /** Prediction algorithms run for vehicles on this feed; the first is used as the default when a query doesn't specify one. */
  predictionAlgorithms: Array<PredictionAlgorithm>;
};

/** The real-time feed protocol an AVL feed is polled and decoded as. */
export type AvlFormat =
  /** GTFS-Realtime VehiclePositions, decoded from the standard protobuf feed. */
  | 'GTFS_RT'
  /** A JSON-based vehicle-position feed format from a specific vendor integration. */
  | 'STPT';

/** GTFS-Realtime VehiclePosition occupancy_status. */
export type AvlOccupancyStatus =
  | 'CRUSHED_STANDING_ROOM_ONLY'
  | 'EMPTY'
  | 'FEW_SEATS_AVAILABLE'
  | 'FULL'
  | 'MANY_SEATS_AVAILABLE'
  | 'NOT_ACCEPTING_PASSENGERS'
  | 'NOT_BOARDABLE'
  | 'NO_DATA_AVAILABLE'
  | 'STANDING_ROOM_ONLY';

/** One raw AVL (vehicle-position) report as received from the feed, plus the outcome of matching it to a trip. */
export type AvlReport = {
  __typename?: 'AvlReport';
  bearing?: Maybe<Scalars['Float']['output']>;
  currentStatus?: Maybe<VehicleStopStatus>;
  /** The trip ID the feed itself reported for this vehicle (its raw descriptor), before any transittrack matching - null if the feed didn't supply one. */
  descTripId?: Maybe<Scalars['String']['output']>;
  /** The resolved trip match, present only when matchStatus is MATCHED. */
  match?: Maybe<AvlReportMatch>;
  matchStatus: MatchStatus;
  occupancyStatus?: Maybe<AvlOccupancyStatus>;
  position: LatLon;
  /** ISO-8601 instant this report was ingested by transittrack. */
  processedAt: Scalars['String']['output'];
  /** The AVL feed protocol this report was decoded from. */
  source: AvlFormat;
  speedMps?: Maybe<Scalars['Float']['output']>;
  /** ISO-8601 instant the vehicle itself reported this position. */
  ts: Scalars['String']['output'];
  vehicleId: Scalars['String']['output'];
};

export type AvlReportCatalog = {
  __typename?: 'AvlReportCatalog';
  /** IANA time zone the feed's agency operates in, used to interpret avlReportPage's date/startTime/endTime. */
  timeZone: Scalars['String']['output'];
  /** Every distinct vehicle ID this feed has ever reported. */
  vehicleIds: Array<Scalars['String']['output']>;
};

/** The resolved trip match for an AvlReport whose matchStatus is MATCHED. */
export type AvlReportMatch = {
  __typename?: 'AvlReportMatch';
  block?: Maybe<Block>;
  /** Perpendicular distance in meters between the raw reported position and the matched shape. */
  deviationM: Scalars['Float']['output'];
  /** Distance in meters along the matched trip's shape. */
  distanceAlongTripM: Scalars['Float']['output'];
  /** Heading in degrees inferred from the match, independent of any bearing the feed itself reported. */
  heading?: Maybe<Scalars['Float']['output']>;
  /** Seconds behind (positive) or ahead of (negative) schedule at this position. */
  scheduleAdherenceSec?: Maybe<Scalars['Int']['output']>;
  /** Match confidence score; higher is a stronger match. Not comparable across matcher implementations. */
  score?: Maybe<Scalars['Float']['output']>;
  /** The raw position snapped onto the matched trip's shape. */
  snappedPosition: LatLon;
  /** Index into the matched trip pattern's stopPaths this report's position falls within. */
  stopPathIndex: Scalars['Int']['output'];
  trip?: Maybe<Trip>;
};

/** A page of AvlReport rows for one vehicle within a requested time window. */
export type AvlReportPage = {
  __typename?: 'AvlReportPage';
  items: Array<AvlReport>;
  page: Scalars['Int']['output'];
  pageSize: Scalars['Int']['output'];
  totalCount: Scalars['Int']['output'];
};

/** A vehicle block: the ordered sequence of trips one vehicle is scheduled to run on a given service day, derived from shared GTFS block_id. */
export type Block = {
  __typename?: 'Block';
  blockId: Scalars['String']['output'];
  /** The block's trips in scheduled order, with inter-trip layover/deadhead detail. */
  blockTrips: Array<BlockTrip>;
  /** Scheduled end of the block's last trip, in seconds since midnight. */
  endTimeSec: Scalars['Int']['output'];
  /** Internal row ID, distinct from blockId (which may repeat across services). */
  id: Scalars['ID']['output'];
  /** Every route served by a trip in this block. */
  routeIds: Array<Scalars['String']['output']>;
  serviceId: Scalars['String']['output'];
  /** Scheduled start of the block's first trip, in seconds since midnight. */
  startTimeSec: Scalars['Int']['output'];
  tripCount: Scalars['Int']['output'];
  /** The block's trips, in no particular guaranteed order; use blockTrips for the scheduled sequence. */
  trips: Array<Trip>;
};

/** One trip's position within its block's scheduled sequence. */
export type BlockTrip = {
  __typename?: 'BlockTrip';
  /** True if the vehicle must deadhead (travel out of service) to reach the next trip's start, or null if not determined. */
  deadheadAfter?: Maybe<Scalars['Boolean']['output']>;
  /** Scheduled layover in seconds between this trip ending and the next one starting, or null for the block's last trip. */
  layoverAfterSec?: Maybe<Scalars['Int']['output']>;
  /** Position of this trip within the block, starting at 0. */
  listIndex: Scalars['Int']['output'];
  trip: Trip;
};

export type BulkShiftTripsInput = {
  /** Signed offset in seconds applied to every matching trip's stop times. */
  deltaSec: Scalars['Int']['input'];
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  /** Restrict to trips of this derived trip pattern. */
  patternKey?: InputMaybe<Scalars['String']['input']>;
  /** Restrict to trips on this route; omit to match across routes. */
  routeId?: InputMaybe<Scalars['String']['input']>;
  serviceId?: InputMaybe<Scalars['String']['input']>;
  /** Restrict to trips starting at or after this time, in seconds since midnight. */
  windowFromSec?: InputMaybe<Scalars['Int']['input']>;
  /** Restrict to trips starting at or before this time, in seconds since midnight. */
  windowToSec?: InputMaybe<Scalars['Int']['input']>;
};

/** GTFS calendar.txt: a named service pattern's days of the week and validity range. */
export type Calendar = {
  __typename?: 'Calendar';
  endDate?: Maybe<Scalars['String']['output']>;
  friday?: Maybe<Scalars['Boolean']['output']>;
  monday?: Maybe<Scalars['Boolean']['output']>;
  saturday?: Maybe<Scalars['Boolean']['output']>;
  serviceId: Scalars['String']['output'];
  startDate?: Maybe<Scalars['String']['output']>;
  sunday?: Maybe<Scalars['Boolean']['output']>;
  thursday?: Maybe<Scalars['Boolean']['output']>;
  tuesday?: Maybe<Scalars['Boolean']['output']>;
  wednesday?: Maybe<Scalars['Boolean']['output']>;
};

/** One calendar's change between the two compared revisions. */
export type CalendarChange = {
  __typename?: 'CalendarChange';
  /** Calendar date exceptions added, removed, or changed for this service. */
  exceptionChanges: Array<CalendarExceptionChange>;
  /** Changed top-level calendar fields (day-of-week flags, startDate, endDate), keyed by field name to {from, to} old/new value pairs. */
  fieldChanges: Scalars['JSON']['output'];
  kind: CalendarChangeKind;
  serviceId: Scalars['String']['output'];
};

/** How a calendar differs between the two compared revisions. */
export type CalendarChangeKind =
  | 'ADDED'
  | 'MODIFIED'
  | 'REMOVED';

/** GTFS calendar_dates.txt: a single-day exception to a service's calendar. exceptionType: 1=service added, 2=service removed. */
export type CalendarDate = {
  __typename?: 'CalendarDate';
  date: Scalars['String']['output'];
  exceptionType?: Maybe<Scalars['Int']['output']>;
  serviceId: Scalars['String']['output'];
};

/** One calendar date exception's change within a changed calendar. */
export type CalendarExceptionChange = {
  __typename?: 'CalendarExceptionChange';
  date: Scalars['String']['output'];
  /** The exception_type (1=added, 2=removed) before this change, or null if it didn't previously exist. */
  fromType?: Maybe<Scalars['Int']['output']>;
  kind: ExceptionChangeKind;
  /** The exception_type after this change, or null if it was removed. */
  toType?: Maybe<Scalars['Int']['output']>;
};

export type DeleteTripInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  tripId: Scalars['String']['input'];
};

/** A revision being edited: a fork of a base revision, plus its edit lock and validation state. */
export type Draft = {
  __typename?: 'Draft';
  /** The revision this draft was forked from, or null if it predates fork tracking. */
  baseRevisionId?: Maybe<Scalars['ID']['output']>;
  createdAt: Scalars['String']['output'];
  /** The editor who created this draft, or null if unknown. */
  createdBy?: Maybe<Scalars['String']['output']>;
  /** True if the draft has been edited since its last rebuildDraft, so its derived schedule data (and validation) may not reflect the latest edits. */
  derivationStale: Scalars['Boolean']['output'];
  feedCode: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  kind: DraftKind;
  /** Operator-facing name for this draft, or null if unset. */
  label?: Maybe<Scalars['String']['output']>;
  /** Validation results from the draft's last rebuildDraft, or null if it hasn't been rebuilt. */
  lastValidation?: Maybe<Scalars['JSON']['output']>;
  /** The current edit lock, or null if no editor currently holds it. */
  lock?: Maybe<DraftLock>;
  /** Row counts per GTFS table, as of the draft's data. */
  rowCounts?: Maybe<Scalars['JSON']['output']>;
  status: RevisionStatus;
  /** Optimistic-concurrency version, incremented by every edit mutation and required (as expectedVersion) by mutations and undo/redo to detect concurrent edits. */
  version: Scalars['Long']['output'];
};

/** One entry in a draft's edit history. */
export type DraftEdit = {
  __typename?: 'DraftEdit';
  appliedAt: Scalars['String']['output'];
  /** Upper-snake-case code identifying the kind of edit (e.g. UPDATE_STOP_TIME, SHIFT_TRIP, ADD_TRIP). */
  op: Scalars['String']['output'];
  /** Sequence number within the draft's edit history, increasing with each new edit. */
  seq: Scalars['Int']['output'];
  /** Human-readable summary of what this edit changed. */
  summary: Scalars['String']['output'];
  /** True if this edit has since been reverted via undoDraftEdit. */
  undone: Scalars['Boolean']['output'];
};

/** The result of an edit mutation: the draft's new state, the edit record it produced, and whether undo/redo are now available. */
export type DraftEditResult = {
  __typename?: 'DraftEditResult';
  canRedo: Scalars['Boolean']['output'];
  canUndo: Scalars['Boolean']['output'];
  draft: Draft;
  /** The edit this mutation recorded, or null for a mutation that doesn't produce an undoable edit. */
  edit?: Maybe<DraftEdit>;
};

/** A draft's trips and stops laid out as an editable grid, scoped to one route/direction/service. */
export type DraftGrid = {
  __typename?: 'DraftGrid';
  /** The scheduled stops shared by every trip in the grid, in stop_sequence order. */
  stops: Array<DraftGridStop>;
  trips: Array<DraftGridTrip>;
};

export type DraftGridCell = {
  __typename?: 'DraftGridCell';
  /** Scheduled arrival in seconds since midnight, or null if this trip doesn't serve this stop. */
  arrivalSec?: Maybe<Scalars['Int']['output']>;
  departureSec?: Maybe<Scalars['Int']['output']>;
  stopSequence: Scalars['Int']['output'];
};

export type DraftGridStop = {
  __typename?: 'DraftGridStop';
  stopId?: Maybe<Scalars['String']['output']>;
  stopName?: Maybe<Scalars['String']['output']>;
  stopSequence: Scalars['Int']['output'];
  /** GTFS timepoint for this stop (0=approximate, 1=exact), or null if not scheduled. */
  timepoint?: Maybe<Scalars['Int']['output']>;
};

/** One trip's row in a draft grid, with one cell per stop in the shared stops list. */
export type DraftGridTrip = {
  __typename?: 'DraftGridTrip';
  blockId?: Maybe<Scalars['String']['output']>;
  /** Cells aligned to the grid's stops list, by stopSequence. */
  cells: Array<DraftGridCell>;
  directionId?: Maybe<Scalars['Int']['output']>;
  /** Seconds since midnight of the trip's first scheduled departure, for sorting rows. */
  firstDepartureSec?: Maybe<Scalars['Int']['output']>;
  headsign?: Maybe<Scalars['String']['output']>;
  serviceId?: Maybe<Scalars['String']['output']>;
  tripId: Scalars['String']['output'];
};

/** Status of an asynchronous rebuildDraft job, pollable via the draftJob query. */
export type DraftJob = {
  __typename?: 'DraftJob';
  /** Failure detail when state is FAILED, or null otherwise. */
  error?: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  phase: DraftJobPhase;
  state: DraftJobState;
};

/** Which step of a rebuildDraft job is currently executing (or finished at). */
export type DraftJobPhase =
  /** Re-deriving schedule data (trip patterns, blocks, travel times, etc.) from the edited tables. */
  | 'DERIVING'
  | 'DONE'
  /** Re-running GTFS validation against the re-derived data. */
  | 'VALIDATING';

/** Status of an asynchronous rebuildDraft job. */
export type DraftJobState =
  /** Failed; see DraftJob.error. */
  | 'FAILED'
  | 'RUNNING'
  | 'SUCCEEDED';

/** Whether a revision is a normal feed ingest or a user-created draft. */
export type DraftKind =
  /** Created via forkDraft (or by an optimization's applyOptimizationRecommendations) and editable through the draft mutations. */
  | 'DRAFT'
  /** Produced by ingestFeed; not directly editable. */
  | 'IMPORT';

/** The exclusive edit lock currently held on a draft. */
export type DraftLock = {
  __typename?: 'DraftLock';
  editor: Scalars['String']['output'];
  /** ISO-8601 instant the lock expires unless renewed via renewDraftEditor. */
  expiresAt: Scalars['String']['output'];
};

export type DuplicateTripInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  /** Explicit trip ID for the copy, or omit to have one generated. */
  newTripId?: InputMaybe<Scalars['String']['input']>;
  /** Signed offset in seconds applied to every stop time of the copy, relative to sourceTripId. */
  offsetSec: Scalars['Int']['input'];
  sourceTripId: Scalars['String']['input'];
};

/** How many analyzer findings one entity has, and the most severe of them. */
export type EntityFindingCount = {
  __typename?: 'EntityFindingCount';
  count: Scalars['Int']['output'];
  entityId: Scalars['String']['output'];
  worstSeverity: FindingSeverity;
};

/** How a calendar date exception differs between the two compared revisions. */
export type ExceptionChangeKind =
  | 'EXCEPTION_ADDED'
  | 'EXCEPTION_CHANGED'
  | 'EXCEPTION_REMOVED';

/** Geographic bounding box, in decimal degrees. */
export type Extent = {
  __typename?: 'Extent';
  maxLat: Scalars['Float']['output'];
  maxLon: Scalars['Float']['output'];
  minLat: Scalars['Float']['output'];
  minLon: Scalars['Float']['output'];
};

/** A configured GTFS static feed: where its data comes from, and its ingest history. */
export type Feed = {
  __typename?: 'Feed';
  /** The feed's current live revision, or null if none has ever been activated. */
  activeRevision?: Maybe<Revision>;
  /** Unique identifier for this feed, referenced by AvlFeed.gtfsFeedCode and throughout the GTFS query API. */
  code: Scalars['String']['output'];
  description?: Maybe<Scalars['String']['output']>;
  enabled: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  /** Cron expression controlling how often this feed is automatically re-ingested, or null if it's only ingested on demand. */
  pollingCron?: Maybe<Scalars['String']['output']>;
  /** Every revision ever created for this feed, newest first. */
  revisions: Array<Revision>;
  source: FeedSource;
  /** URL the feed's GTFS zip is downloaded from on ingest. */
  url: Scalars['String']['output'];
};

/** GTFS feed_info.txt: feed-level publisher metadata. */
export type FeedInfo = {
  __typename?: 'FeedInfo';
  defaultLang?: Maybe<Scalars['String']['output']>;
  feedContactEmail?: Maybe<Scalars['String']['output']>;
  feedContactUrl?: Maybe<Scalars['String']['output']>;
  feedEndDate?: Maybe<Scalars['String']['output']>;
  feedLang?: Maybe<Scalars['String']['output']>;
  feedPublisherName?: Maybe<Scalars['String']['output']>;
  feedPublisherUrl?: Maybe<Scalars['String']['output']>;
  feedStartDate?: Maybe<Scalars['String']['output']>;
  feedVersion?: Maybe<Scalars['String']['output']>;
};

/** How a GTFS feed is managed: hand-configured versus created through the registerFeed API. */
export type FeedSource =
  | 'API'
  | 'CONFIG';

/** The analyzer's own severity scale (the API's `Severity` collapses it to three levels). */
export type FindingSeverity =
  | 'CRITICAL'
  | 'HIGH'
  | 'INFO'
  | 'LOW'
  | 'MEDIUM';

export type ForkDraftInput = {
  /** Revision to fork from, or omit to fork the feed's current active revision. */
  baseRevisionId?: InputMaybe<Scalars['ID']['input']>;
  editor: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  label?: InputMaybe<Scalars['String']['input']>;
};

/** GTFS frequencies.txt: a headway-based (rather than fixed-time) scheduling window for a trip. exactTimes: 0=frequency-based (approximate), 1=schedule-based (exact). */
export type Frequency = {
  __typename?: 'Frequency';
  endTime?: Maybe<Scalars['String']['output']>;
  exactTimes?: Maybe<Scalars['Int']['output']>;
  headwaySecs?: Maybe<Scalars['Int']['output']>;
  startTime: Scalars['String']['output'];
  tripId: Scalars['String']['output'];
};

/** Current gap between consecutive vehicles serving a route/direction at a stop, compared to what the schedule calls for. */
export type Headway = {
  __typename?: 'Headway';
  directionId?: Maybe<Scalars['Int']['output']>;
  /** Observed gaps in seconds between the vehicles currently predicted at this stop, in arrival order. */
  gapsSec: Array<Scalars['Int']['output']>;
  routeId: Scalars['String']['output'];
  /** The schedule's own average headway at this stop, in seconds, for comparison: trips running that service day, from an hour before the first predicted arrival to an hour after the last (around now if none). Frequency-based trips are left out. */
  scheduledHeadwaySec?: Maybe<Scalars['Int']['output']>;
  stopId: Scalars['String']['output'];
  /** Seconds until the next vehicle is predicted to reach this stop, or null if none is currently predicted. */
  waitSec?: Maybe<Scalars['Int']['output']>;
};

/** Scheduled headway comparison for one route/direction/service between the two compared revisions. */
export type HeadwaySummary = {
  __typename?: 'HeadwaySummary';
  directionId?: Maybe<Scalars['Int']['output']>;
  /** Largest gap between consecutive trips in fromRevisionId, in seconds. */
  fromMaxGapSec?: Maybe<Scalars['Int']['output']>;
  /** Mean gap between consecutive trips in fromRevisionId, in seconds. */
  fromMeanGapSec?: Maybe<Scalars['Int']['output']>;
  fromTripCount: Scalars['Int']['output'];
  routeId: Scalars['String']['output'];
  serviceId: Scalars['String']['output'];
  toMaxGapSec?: Maybe<Scalars['Int']['output']>;
  toMeanGapSec?: Maybe<Scalars['Int']['output']>;
  toTripCount: Scalars['Int']['output'];
};

export type InsertTripStopInput = {
  /** The existing stopSequence the new stop is inserted after. */
  afterStopSequence: Scalars['Int']['input'];
  arrivalSec?: InputMaybe<Scalars['Int']['input']>;
  departureSec?: InputMaybe<Scalars['Int']['input']>;
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  stopId: Scalars['String']['input'];
  tripId: Scalars['String']['input'];
};

export type LatLon = {
  __typename?: 'LatLon';
  lat: Scalars['Float']['output'];
  lon: Scalars['Float']['output'];
};

/** GTFS levels.txt: one floor/level of a station, for indoor wayfinding. */
export type Level = {
  __typename?: 'Level';
  levelId: Scalars['String']['output'];
  levelIndex?: Maybe<Scalars['Float']['output']>;
  levelName?: Maybe<Scalars['String']['output']>;
};

/** Outcome of attempting to match one AVL report to a scheduled trip. */
export type MatchStatus =
  /** Successfully matched to a trip; see AvlReport.match for the resulting position. */
  | 'MATCHED'
  /** Not yet processed by the matcher. */
  | 'PENDING'
  /** Not matched because the report was superseded or otherwise excluded before matching ran. */
  | 'SKIPPED'
  /** Processed, but no trip could be matched (e.g. vehicle off-route or outside any active trip's window). */
  | 'UNMATCHED';

export type Mutation = {
  __typename?: 'Mutation';
  /** Promotes a draft to be the feed's active revision. Fails if the draft has unresolved validation errors unless force is true. */
  activateDraft: Revision;
  /** Promotes a READY revision to ACTIVE, superseding the feed's previously active revision. */
  activateRevision: Revision;
  /** Adds a new trip to the draft. */
  addTrip: DraftEditResult;
  /** Applies the given recommendations from a completed run to a new draft forked from the run's revision, marking them APPLIED. */
  applyOptimizationRecommendations: Draft;
  /** Shifts every trip matching the given route/pattern/service and time window by a fixed offset, in one edit. */
  bulkShiftTrips: DraftEditResult;
  /** Acquires the draft's exclusive edit lock for editor. Fails if another editor holds it, unless takeOver is true. */
  claimDraftEditor: DraftLock;
  /** Deletes a feed and all its revisions. Returns false if code is unknown. */
  deleteFeed: Scalars['Boolean']['output'];
  /** Permanently deletes a revision and its derived data. Fails on the feed's currently ACTIVE revision. */
  deleteRevision: Scalars['Boolean']['output'];
  deleteTrip: DraftEditResult;
  /** Permanently deletes a draft and its edit history. Returns false if id is unknown. editor must hold the draft's edit lock. */
  discardDraft: Scalars['Boolean']['output'];
  /** Copies an existing trip under a new trip ID, with every stop time shifted by offsetSec. */
  duplicateTrip: DraftEditResult;
  /** Creates a new draft forked from a revision (the feed's active one if baseRevisionId is omitted). */
  forkDraft: Draft;
  /** Downloads, validates, and processes the feed's current GTFS zip into a new revision; activates it automatically if the feed has autoActivate set and validation passes. */
  ingestFeed: Revision;
  /** Inserts a new scheduled stop into a trip immediately after afterStopSequence, renumbering later stops. */
  insertTripStop: DraftEditResult;
  /** Asynchronously re-derives a draft's schedule data and re-validates it. Returns a DraftJob to poll via draftJob. */
  rebuildDraft: DraftJob;
  /** Re-applies the most recently undone edit. */
  redoDraftEdit: DraftEditResult;
  /** Registers a new GTFS feed to ingest. Fails if code is already taken. */
  registerFeed: Feed;
  /** Releases editor's lock on the draft. Returns false if editor does not hold it. */
  releaseDraftEditor: Scalars['Boolean']['output'];
  /** Removes one scheduled stop from a trip, renumbering later stops. */
  removeTripStop: DraftEditResult;
  /** Extends the expiry of editor's existing lock on the draft. */
  renewDraftEditor: DraftLock;
  /** Reorders a trip's scheduled stops to match stopIdOrder. */
  reorderTripStops: DraftEditResult;
  /** Discards all edits since the draft was forked, resetting it back to its base revision's data. */
  revertDraftToFork: Draft;
  /** Replaces a service's weekly calendar pattern and validity range. */
  setCalendar: DraftEditResult;
  /** Adds, changes, or removes a single-day calendar exception for a service. */
  setCalendarException: DraftEditResult;
  /** Sets how long a trip dwells at a stop by adjusting its departure relative to its arrival. */
  setStopDwell: DraftEditResult;
  /** Shifts every stop time of a trip by a fixed offset. */
  shiftTrip: DraftEditResult;
  /** Submits a new optimization run for asynchronous processing. */
  startOptimizationRun: OptimizationRun;
  /** Reverts the draft's most recent not-yet-undone edit. Fails with a version conflict if expectedVersion is stale. */
  undoDraftEdit: DraftEditResult;
  /** Updates a feed's configuration. Fails if code is unknown. */
  updateFeed: Feed;
  /** Changes one scheduled stop time on a trip. Fails with a version conflict if expectedVersion no longer matches the draft's current version (see DraftDto.version / StaleDraftException). */
  updateStopTime: DraftEditResult;
};


export type MutationActivateDraftArgs = {
  editor: Scalars['String']['input'];
  force?: InputMaybe<Scalars['Boolean']['input']>;
  id: Scalars['ID']['input'];
};


export type MutationActivateRevisionArgs = {
  revisionId: Scalars['ID']['input'];
};


export type MutationAddTripArgs = {
  input: AddTripInput;
};


export type MutationApplyOptimizationRecommendationsArgs = {
  editor: Scalars['String']['input'];
  label?: InputMaybe<Scalars['String']['input']>;
  recommendationIds: Array<Scalars['ID']['input']>;
  runId: Scalars['ID']['input'];
};


export type MutationBulkShiftTripsArgs = {
  input: BulkShiftTripsInput;
};


export type MutationClaimDraftEditorArgs = {
  editor: Scalars['String']['input'];
  id: Scalars['ID']['input'];
  takeOver?: InputMaybe<Scalars['Boolean']['input']>;
};


export type MutationDeleteFeedArgs = {
  code: Scalars['String']['input'];
};


export type MutationDeleteRevisionArgs = {
  revisionId: Scalars['ID']['input'];
};


export type MutationDeleteTripArgs = {
  input: DeleteTripInput;
};


export type MutationDiscardDraftArgs = {
  editor: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


export type MutationDuplicateTripArgs = {
  input: DuplicateTripInput;
};


export type MutationForkDraftArgs = {
  input: ForkDraftInput;
};


export type MutationIngestFeedArgs = {
  feedCode: Scalars['String']['input'];
};


export type MutationInsertTripStopArgs = {
  input: InsertTripStopInput;
};


export type MutationRebuildDraftArgs = {
  id: Scalars['ID']['input'];
};


export type MutationRedoDraftEditArgs = {
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  id: Scalars['ID']['input'];
};


export type MutationRegisterFeedArgs = {
  input: RegisterFeedInput;
};


export type MutationReleaseDraftEditorArgs = {
  editor: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


export type MutationRemoveTripStopArgs = {
  input: RemoveTripStopInput;
};


export type MutationRenewDraftEditorArgs = {
  editor: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


export type MutationReorderTripStopsArgs = {
  input: ReorderTripStopsInput;
};


export type MutationRevertDraftToForkArgs = {
  editor: Scalars['String']['input'];
  id: Scalars['ID']['input'];
};


export type MutationSetCalendarArgs = {
  input: SetCalendarInput;
};


export type MutationSetCalendarExceptionArgs = {
  input: SetCalendarExceptionInput;
};


export type MutationSetStopDwellArgs = {
  input: SetStopDwellInput;
};


export type MutationShiftTripArgs = {
  input: ShiftTripInput;
};


export type MutationStartOptimizationRunArgs = {
  input: OptimizationRunInput;
};


export type MutationUndoDraftEditArgs = {
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  id: Scalars['ID']['input'];
};


export type MutationUpdateFeedArgs = {
  code: Scalars['String']['input'];
  input: UpdateFeedInput;
};


export type MutationUpdateStopTimeArgs = {
  input: UpdateStopTimeInput;
};

/** One scheduled stop for a new trip created via addTrip, in stop_sequence order. */
export type NewStopTimeInput = {
  arrivalSec: Scalars['Int']['input'];
  departureSec: Scalars['Int']['input'];
  stopId: Scalars['String']['input'];
};

/** One proposed schedule adjustment produced by an optimization run, backed by observed AVL evidence. */
export type OptimizationRecommendation = {
  __typename?: 'OptimizationRecommendation';
  /** The current schedule value(s) this recommendation would change. */
  currentValue: Scalars['JSON']['output'];
  /** Proposed change in seconds, signed by kind (e.g. a stop-time or trip-start adjustment). */
  deltaSec: Scalars['Int']['output'];
  /** Supporting observed-data detail behind this recommendation. */
  evidence: Scalars['JSON']['output'];
  id: Scalars['ID']['output'];
  kind: OptimizationRecommendationKind;
  /** The proposed replacement value(s). */
  proposedValue: Scalars['JSON']['output'];
  /** Human-readable explanation of why this recommendation was made. */
  reason: Scalars['String']['output'];
  /** Number of observed samples this recommendation is based on. */
  sampleCount: Scalars['Int']['output'];
  status: OptimizationRecommendationStatus;
};

/** The kind of schedule adjustment an optimization recommendation proposes. */
export type OptimizationRecommendationKind =
  /** Adjusts a scheduled stop's arrival/departure time. */
  | 'STOP_TIME'
  /** Shifts a trip's start time. */
  | 'TRIP_SHIFT';

/** Review/application state of an optimization recommendation. */
export type OptimizationRecommendationStatus =
  /** Applied to a draft via applyOptimizationRecommendations. */
  | 'APPLIED'
  /** Reviewed and rejected. */
  | 'DISMISSED'
  /** Not yet reviewed. */
  | 'PENDING'
  /** Chosen by a reviewer for application, but not yet applied. */
  | 'SELECTED'
  /** No longer applicable (e.g. its underlying trip was removed from the schedule since the run completed). */
  | 'UNAVAILABLE';

/** One asynchronous schedule-optimization analysis, frozen to the feed's active revision at submission time. */
export type OptimizationRun = {
  __typename?: 'OptimizationRun';
  /** ISO-8601 instant the run finished (successfully or not), or null while still QUEUED/RUNNING. */
  completedAt?: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['String']['output'];
  /** Failure detail when state is FAILED, or null otherwise. */
  error?: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  /** The revision this run's recommendations apply against. */
  revisionId: Scalars['ID']['output'];
  state: OptimizationRunState;
};

/** Request to analyze observed AVL data against a feed's current schedule for a window of trips, looking for timing adjustments. */
export type OptimizationRunInput = {
  directionId?: InputMaybe<Scalars['Int']['input']>;
  feedCode: Scalars['String']['input'];
  /** Minimum number of observed samples required before a recommendation is produced for a given stop/trip. */
  minimumSamples: Scalars['Int']['input'];
  /** ISO-8601 instant */
  observedFrom: Scalars['String']['input'];
  /** ISO-8601 instant */
  observedTo: Scalars['String']['input'];
  routeId?: InputMaybe<Scalars['String']['input']>;
  /** Restrict analysis to this service; omit to cover every service. */
  serviceId?: InputMaybe<Scalars['String']['input']>;
  /** Restrict to trips starting at or after this time, in seconds since midnight. */
  windowFromSec?: InputMaybe<Scalars['Int']['input']>;
  windowToSec?: InputMaybe<Scalars['Int']['input']>;
};

/** Lifecycle state of an asynchronous optimization run. */
export type OptimizationRunState =
  /** Failed; see OptimizationRun.error. */
  | 'FAILED'
  /** Submitted, not yet started. */
  | 'QUEUED'
  /** Analyzing observed AVL data against the schedule. */
  | 'RUNNING'
  /** Finished; see optimizationRecommendations for its findings. */
  | 'SUCCEEDED';

/**
 * GTFS pathways.txt: a connection (corridor, stairs, escalator, elevator, etc.) between two
 * stops/stations/entrances for indoor wayfinding. pathwayMode: 1=walkway, 2=stairs, 3=moving
 * sidewalk, 4=escalator, 5=elevator, 6=fare gate, 7=exit gate. isBidirectional: 0=unidirectional, 1=bidirectional.
 */
export type Pathway = {
  __typename?: 'Pathway';
  fromStopId?: Maybe<Scalars['String']['output']>;
  isBidirectional?: Maybe<Scalars['Int']['output']>;
  length?: Maybe<Scalars['Float']['output']>;
  maxSlope?: Maybe<Scalars['Float']['output']>;
  minWidth?: Maybe<Scalars['Float']['output']>;
  pathwayId: Scalars['String']['output'];
  pathwayMode?: Maybe<Scalars['Int']['output']>;
  reversedSignpostedAs?: Maybe<Scalars['String']['output']>;
  signpostedAs?: Maybe<Scalars['String']['output']>;
  stairCount?: Maybe<Scalars['Int']['output']>;
  toStopId?: Maybe<Scalars['String']['output']>;
  traversalTime?: Maybe<Scalars['Int']['output']>;
};

/** Prediction error for snapshots made at most horizonSec (and more than the next shorter horizon) before the actual crossing. */
export type PredictionAccuracyHorizon = {
  __typename?: 'PredictionAccuracyHorizon';
  confidenceCoverage?: Maybe<Scalars['Float']['output']>;
  confidenceSampleCount: Scalars['Int']['output'];
  eligibleCount: Scalars['Int']['output'];
  horizonSec: Scalars['Int']['output'];
  meanAbsErrorSec?: Maybe<Scalars['Float']['output']>;
  /** Mean signed error in seconds (actual minus predicted); positive means vehicles got there later than predicted. */
  meanErrorSec?: Maybe<Scalars['Float']['output']>;
  p90AbsErrorSec?: Maybe<Scalars['Float']['output']>;
  p95AbsErrorSec?: Maybe<Scalars['Float']['output']>;
  pairedSampleCount: Scalars['Int']['output'];
  predictionCoverage: Scalars['Float']['output'];
  sampleCount: Scalars['Int']['output'];
};

/** Aggregate prediction error for one algorithm over a recent time window. Each stop the vehicle reaches is scored once per lead-time horizon a prediction was made within, so the overall figures pool every horizon. */
export type PredictionAccuracySummary = {
  __typename?: 'PredictionAccuracySummary';
  algorithm: PredictionAlgorithm;
  /** The same statistics per lead-time horizon, shortest first. */
  byHorizon: Array<PredictionAccuracyHorizon>;
  /** Observed fraction of errors within the algorithm's one-SD spread, among available forecasts with a spread. */
  confidenceCoverage?: Maybe<Scalars['Float']['output']>;
  confidenceSampleCount: Scalars['Int']['output'];
  /** Eligible shared snapshots, including missing forecasts. Only reliable actual crossings are scored. */
  eligibleCount: Scalars['Int']['output'];
  /** Mean absolute error in seconds. */
  meanAbsErrorSec?: Maybe<Scalars['Float']['output']>;
  /** Mean signed error in seconds (actual minus predicted); positive means vehicles got there later than predicted. */
  meanErrorSec?: Maybe<Scalars['Float']['output']>;
  p90AbsErrorSec?: Maybe<Scalars['Float']['output']>;
  p95AbsErrorSec?: Maybe<Scalars['Float']['output']>;
  /** Snapshots where every compared algorithm produced a forecast. Error statistics use only these paired samples. */
  pairedSampleCount: Scalars['Int']['output'];
  /** Available forecasts divided by eligibleCount; conditional on matched observations, not total scheduled service. */
  predictionCoverage: Scalars['Float']['output'];
  /** Available forecasts across eligible shared snapshots and all horizons. */
  sampleCount: Scalars['Int']['output'];
};

/** A method for predicting a vehicle's arrival/departure time at an upcoming stop. */
export type PredictionAlgorithm =
  /** Predicts using a historical average of observed travel/dwell times for this stop and context. */
  | 'HISTORICAL_AVERAGE'
  /** Predicts using a Kalman filter over observed travel times. */
  | 'KALMAN'
  /** Projects the vehicle's current schedule adherence forward along the rest of its trip. */
  | 'SCHEDULE_ADHERENCE';

export type PredictionDayType =
  | 'SATURDAY'
  | 'SUNDAY'
  | 'WEEKDAY';

export type PredictionTimeBand =
  | 'AFTERNOON_PEAK'
  | 'EVENING'
  | 'MIDDAY'
  | 'MORNING_PEAK'
  | 'NIGHT';

export type Query = {
  __typename?: 'Query';
  /** Transit agencies defined in the feed's active (or given) revision. */
  agencies: Array<Agency>;
  /** Per-route adherence aggregates over a date range. */
  allRoutesAdherence: Array<RouteAdherenceSummary>;
  /** Per-trip-execution adherence aggregates over a date range. Pass routeId to scope to one route. */
  allTripsAdherence: Array<TripAdherenceSummary>;
  /** Every configured AVL (vehicle-position) feed, with its decode/match configuration and last poll outcome. */
  avlFeeds: Array<AvlFeed>;
  /** The feed's configured time zone and every distinct vehicle ID it has ever reported, for populating report-browsing filters. */
  avlReportCatalog: AvlReportCatalog;
  /** A page of raw AVL reports for one vehicle within a local time-of-day window (startTime/endTime) on a given date. */
  avlReportPage: AvlReportPage;
  /** Raw AVL report history for one vehicle, newest first. since is an ISO-8601 instant; omit it to return the most recent reports up to limit. */
  avlReports: Array<AvlReport>;
  block?: Maybe<Block>;
  /** Derived vehicle blocks (sequences of trips run by one vehicle) in the feed's active (or given) revision. */
  blocks: Array<Block>;
  /** Every block that operates on a given calendar date. */
  blocksOnDate: Array<Block>;
  /** Calendar date exceptions (added/removed single-day service), optionally filtered to one serviceId. */
  calendarDates: Array<CalendarDate>;
  /** Service calendars (weekly patterns) in the feed's active (or given) revision. */
  calendars: Array<Calendar>;
  /** Diffs one revision's derived schedule against another's: trip and calendar changes, plus headway deltas. Scope with routeId/directionId/serviceId; tripChanges is paginated via offset/limit while tripChangeCount reports the unpaginated total. */
  compareRevisions: ScheduleComparison;
  /** A single draft by ID, or null if unknown. */
  draft?: Maybe<Draft>;
  /** A draft's edit history, newest first, including undone edits. */
  draftEdits: Array<DraftEdit>;
  /** A draft's editable trip/stop-time grid for one route/direction/service, for the schedule editor UI. */
  draftGrid: DraftGrid;
  /** Status of an async rebuildDraft job, or null if jobId is unknown (e.g. it has aged out of the in-memory job registry). */
  draftJob?: Maybe<DraftJob>;
  /** Drafts (revisions with kind DRAFT), newest first. Pass feedCode to scope to one feed. */
  drafts: Array<Draft>;
  /** A single feed by its code, or null if unknown. */
  feed?: Maybe<Feed>;
  /** The feed's whole network layer as one GeoJSON FeatureCollection (trip-pattern LineStrings, simplified to ~3 m, plus boarding-stop Points), gzip-compressed then base64-encoded. Each feature has a `kind` property: `pattern` or `stop`. */
  feedGeometry: Scalars['String']['output'];
  /** Feed-level publisher/validity metadata, or null if the feed's GTFS didn't include a feed_info.txt. */
  feedInfo?: Maybe<FeedInfo>;
  /** Every configured GTFS static feed. */
  feeds: Array<Feed>;
  /** Frequency-based scheduling entries, optionally filtered to one trip. */
  frequencies: Array<Frequency>;
  /** Current headway (gap between consecutive vehicles) at a stop for one route/direction. */
  headway: Headway;
  levels: Array<Level>;
  /** A run's recommendations. Pass status to filter. */
  optimizationRecommendations: Array<OptimizationRecommendation>;
  /** A single run by ID, or null if unknown. */
  optimizationRun?: Maybe<OptimizationRun>;
  /** Optimization runs for a feed, newest first. */
  optimizationRuns: Array<OptimizationRun>;
  pathways: Array<Pathway>;
  /** Recent, paired prediction-error statistics, overall and by actual lead time. Compares currently configured algorithms unless algorithm restricts to one. Optional route, direction and observation-time context filters apply before pairing. */
  predictionAccuracy: Array<PredictionAccuracySummary>;
  /** Raw rows from one of the less commonly queried GTFS tables (fares v2, flex, translations, attributions), returned as untyped JSON since their shapes vary by GTFS extension. */
  records: Array<Scalars['JSON']['output']>;
  /** A single revision by its ID, or null if unknown. */
  revision?: Maybe<Revision>;
  /** A feed's revisions (ingest attempts and drafts), newest first. Pass status to filter. */
  revisions: Array<Revision>;
  /** A single route by ID, or null if unknown in that revision. */
  route?: Maybe<Route>;
  /** Per-stop adherence detail for one route over a date range. */
  routeAdherence: Array<StopPassage>;
  /** Routes defined in the feed's active (or given) revision. */
  routes: Array<Route>;
  /** A trip shape's metadata and geometry by ID, or null if unknown. */
  shape?: Maybe<Shape>;
  stop?: Maybe<Stop>;
  /** Per-stop adherence detail for one stop over a date range, across every trip serving it. */
  stopAdherence: Array<StopPassage>;
  /** Open predictions for every vehicle currently approaching a given stop, for a stop-board display. routeId/directionId scope which trip patterns at the stop are considered; omitting routeId returns no predictions. algorithm defaults to the feed's first configured algorithm. */
  stopPredictions: Array<StopPrediction>;
  /** Every scheduled stop of one trip, in stop_sequence order. */
  stopTimes: Array<StopTime>;
  stops: Array<Stop>;
  transfers: Array<Transfer>;
  trip?: Maybe<Trip>;
  /** Per-stop adherence detail for one trip over a date range (one trip execution per service date it ran). */
  tripAdherence: Array<StopPassage>;
  /** A single trip pattern by its key, or null if unknown. */
  tripPattern?: Maybe<TripPattern>;
  /** Derived trip patterns (distinct stop sequences) in the feed's active (or given) revision. Pass routeId to scope to one route. */
  tripPatterns: Array<TripPattern>;
  /** Trips in the feed's active (or given) revision. Filter by routeId and/or serviceId. */
  trips: Array<Trip>;
  /** Every trip that operates on a given calendar date. Pass routeId to scope to one route. */
  tripsOnDate: Array<Trip>;
  /** Which entities of one type (Trip, Stop, Route, ...) have analyzer findings, with counts, for filtering lists to the ones with problems. */
  validationFindingCounts: Array<EntityFindingCount>;
  /** Analyzer findings from the feed's active (or given) revision, most severe first. Filter by entityType (e.g. Trip) and/or entityId. */
  validationFindings: Array<ValidationFinding>;
  /** A single vehicle's latest known state, or null if the feed or vehicle is unknown. */
  vehicle?: Maybe<Vehicle>;
  /** Predicted and reached stops of one vehicle's current trip. Pass algorithm to restrict to a single prediction algorithm. */
  vehiclePredictions: Array<StopPrediction>;
  /** Vehicles currently reporting on this feed, from each vehicle's latest known state rather than raw history. Set matchedOnly to only return vehicles currently matched to a trip. */
  vehicles: Array<Vehicle>;
};


export type QueryAgenciesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryAllRoutesAdherenceArgs = {
  endDate: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  startDate: Scalars['String']['input'];
};


export type QueryAllTripsAdherenceArgs = {
  endDate: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  routeId?: InputMaybe<Scalars['String']['input']>;
  startDate: Scalars['String']['input'];
};


export type QueryAvlReportCatalogArgs = {
  feedCode: Scalars['String']['input'];
};


export type QueryAvlReportPageArgs = {
  date: Scalars['String']['input'];
  endTime: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  page?: InputMaybe<Scalars['Int']['input']>;
  pageSize?: InputMaybe<Scalars['Int']['input']>;
  startTime: Scalars['String']['input'];
  vehicleId: Scalars['String']['input'];
};


export type QueryAvlReportsArgs = {
  feedCode: Scalars['String']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  since?: InputMaybe<Scalars['String']['input']>;
  vehicleId: Scalars['String']['input'];
};


export type QueryBlockArgs = {
  blockId: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  serviceId: Scalars['String']['input'];
};


export type QueryBlocksArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryBlocksOnDateArgs = {
  date: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryCalendarDatesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  serviceId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryCalendarsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryCompareRevisionsArgs = {
  directionId?: InputMaybe<Scalars['Int']['input']>;
  fromRevisionId: Scalars['ID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  offset?: InputMaybe<Scalars['Int']['input']>;
  routeId?: InputMaybe<Scalars['String']['input']>;
  serviceId?: InputMaybe<Scalars['String']['input']>;
  toRevisionId: Scalars['ID']['input'];
};


export type QueryDraftArgs = {
  id: Scalars['ID']['input'];
};


export type QueryDraftEditsArgs = {
  id: Scalars['ID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryDraftGridArgs = {
  directionId?: InputMaybe<Scalars['Int']['input']>;
  draftId: Scalars['ID']['input'];
  routeId: Scalars['String']['input'];
  serviceId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryDraftJobArgs = {
  jobId: Scalars['ID']['input'];
};


export type QueryDraftsArgs = {
  feedCode?: InputMaybe<Scalars['String']['input']>;
};


export type QueryFeedArgs = {
  code: Scalars['String']['input'];
};


export type QueryFeedGeometryArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryFeedInfoArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryFrequenciesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  tripId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryHeadwayArgs = {
  algorithm?: InputMaybe<PredictionAlgorithm>;
  directionId?: InputMaybe<Scalars['Int']['input']>;
  feedCode: Scalars['String']['input'];
  routeId: Scalars['String']['input'];
  stopId: Scalars['String']['input'];
};


export type QueryLevelsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryOptimizationRecommendationsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  offset?: InputMaybe<Scalars['Int']['input']>;
  runId: Scalars['ID']['input'];
  status?: InputMaybe<OptimizationRecommendationStatus>;
};


export type QueryOptimizationRunArgs = {
  id: Scalars['ID']['input'];
};


export type QueryOptimizationRunsArgs = {
  feedCode: Scalars['String']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  offset?: InputMaybe<Scalars['Int']['input']>;
};


export type QueryPathwaysArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryPredictionAccuracyArgs = {
  algorithm?: InputMaybe<PredictionAlgorithm>;
  dayType?: InputMaybe<PredictionDayType>;
  directionId?: InputMaybe<Scalars['Int']['input']>;
  feedCode: Scalars['String']['input'];
  routeId?: InputMaybe<Scalars['String']['input']>;
  sinceDays?: InputMaybe<Scalars['Int']['input']>;
  timeBand?: InputMaybe<PredictionTimeBand>;
};


export type QueryRecordsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  table: Table;
};


export type QueryRevisionArgs = {
  id: Scalars['ID']['input'];
};


export type QueryRevisionsArgs = {
  feedCode: Scalars['String']['input'];
  status?: InputMaybe<RevisionStatus>;
};


export type QueryRouteArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  routeId: Scalars['String']['input'];
};


export type QueryRouteAdherenceArgs = {
  endDate: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  routeId: Scalars['String']['input'];
  startDate: Scalars['String']['input'];
};


export type QueryRoutesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryShapeArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  shapeId: Scalars['String']['input'];
};


export type QueryStopArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  stopId: Scalars['String']['input'];
};


export type QueryStopAdherenceArgs = {
  endDate: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  startDate: Scalars['String']['input'];
  stopId: Scalars['String']['input'];
};


export type QueryStopPredictionsArgs = {
  algorithm?: InputMaybe<PredictionAlgorithm>;
  directionId?: InputMaybe<Scalars['Int']['input']>;
  feedCode: Scalars['String']['input'];
  routeId?: InputMaybe<Scalars['String']['input']>;
  stopId: Scalars['String']['input'];
};


export type QueryStopTimesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  tripId: Scalars['String']['input'];
};


export type QueryStopsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTransfersArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTripArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  tripId: Scalars['String']['input'];
};


export type QueryTripAdherenceArgs = {
  endDate: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  startDate: Scalars['String']['input'];
  tripId: Scalars['String']['input'];
};


export type QueryTripPatternArgs = {
  feedCode: Scalars['String']['input'];
  patternKey: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryTripPatternsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  routeId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryTripsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  routeId?: InputMaybe<Scalars['String']['input']>;
  serviceId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryTripsOnDateArgs = {
  date: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
  routeId?: InputMaybe<Scalars['String']['input']>;
};


export type QueryValidationFindingCountsArgs = {
  entityType: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryValidationFindingsArgs = {
  entityId?: InputMaybe<Scalars['String']['input']>;
  entityType?: InputMaybe<Scalars['String']['input']>;
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryVehicleArgs = {
  feedCode: Scalars['String']['input'];
  vehicleId: Scalars['String']['input'];
};


export type QueryVehiclePredictionsArgs = {
  algorithm?: InputMaybe<PredictionAlgorithm>;
  feedCode: Scalars['String']['input'];
  vehicleId: Scalars['String']['input'];
};


export type QueryVehiclesArgs = {
  feedCode: Scalars['String']['input'];
  matchedOnly?: InputMaybe<Scalars['Boolean']['input']>;
};

export type RegisterFeedInput = {
  /** If true, ingestFeed automatically activates a successfully validated revision instead of leaving it READY. */
  autoActivate?: InputMaybe<Scalars['Boolean']['input']>;
  code: Scalars['String']['input'];
  description?: InputMaybe<Scalars['String']['input']>;
  enabled?: InputMaybe<Scalars['Boolean']['input']>;
  name: Scalars['String']['input'];
  pollingCron?: InputMaybe<Scalars['String']['input']>;
  url: Scalars['String']['input'];
};

export type RemoveTripStopInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  stopSequence: Scalars['Int']['input'];
  tripId: Scalars['String']['input'];
};

export type ReorderTripStopsInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  /** Every stop ID of the trip, in the desired new order. */
  stopIdOrder: Array<Scalars['String']['input']>;
  tripId: Scalars['String']['input'];
};

/** One ingest attempt (or draft) of a feed's GTFS data, with its validation and processing outcome. */
export type Revision = {
  __typename?: 'Revision';
  /** ISO-8601 instant this revision became ACTIVE, or null if it never was. */
  activatedAt?: Maybe<Scalars['String']['output']>;
  /** Size of the downloaded GTFS zip in bytes. */
  byteSize?: Maybe<Scalars['Int']['output']>;
  /** SHA-256 of the downloaded GTFS zip, used to detect byte-identical re-ingests (see RevisionStatus.UNCHANGED). */
  contentSha256?: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['String']['output'];
  /** Failure detail when status is FAILED, or null otherwise. */
  errorMessage?: Maybe<Scalars['String']['output']>;
  feedCode: Scalars['String']['output'];
  /** Latest service date covered by this revision's calendars. */
  feedEndDate?: Maybe<Scalars['String']['output']>;
  /** Earliest service date covered by this revision's calendars, per feed_info/calendar. */
  feedStartDate?: Maybe<Scalars['String']['output']>;
  /** Names of the GTFS files present in this revision's zip. */
  filesPresent: Array<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  /** Number of rows loaded per GTFS table. */
  rowCounts?: Maybe<Scalars['JSON']['output']>;
  status: RevisionStatus;
  /** ISO-8601 instant this revision was superseded by a later activation, or null if it hasn't been. */
  supersededAt?: Maybe<Scalars['String']['output']>;
  /** Validation results from the most recent GTFS validator run against this revision, or null if it hasn't been validated. */
  validationSummary?: Maybe<ValidationSummary>;
};

/** Lifecycle state of a GTFS revision, from download through activation (or a draft's own edit/rebuild cycle). */
export type RevisionStatus =
  /** The feed's current, live revision. */
  | 'ACTIVE'
  /** Computing derived schedule data (trip patterns, blocks, travel times, etc.) from the parsed tables. */
  | 'DERIVING'
  /** Downloading the GTFS zip from the feed's URL. */
  | 'DOWNLOADING'
  /** A draft: forked from a revision for editing, not the result of an automatic ingest. */
  | 'DRAFT'
  /** Processing failed at some stage; see errorMessage. */
  | 'FAILED'
  /** Loading validated GTFS files into the database. */
  | 'PARSING'
  /** Queued, not yet started. */
  | 'PENDING'
  /** Fully processed and eligible for activation, but not yet the feed's active revision. */
  | 'READY'
  /** Was previously ACTIVE; replaced by a later activation. */
  | 'SUPERSEDED'
  /** The downloaded content was byte-identical to the feed's current active revision, so no new revision was created. */
  | 'UNCHANGED'
  /** Running the GTFS validator against the downloaded zip. */
  | 'VALIDATING';

/**
 * GTFS routes.txt: a named, user-facing service (e.g. a bus line). routeType follows the GTFS
 * extended route_type vocabulary (0=tram, 1=subway, 2=rail, 3=bus, 4=ferry, 5=cable tram, 6=aerial
 * lift, 7=funicular, 11=trolleybus, 12=monorail).
 */
export type Route = {
  __typename?: 'Route';
  agency?: Maybe<Agency>;
  agencyId?: Maybe<Scalars['String']['output']>;
  extent?: Maybe<Extent>;
  networkId?: Maybe<Scalars['String']['output']>;
  routeColor?: Maybe<Scalars['String']['output']>;
  routeDesc?: Maybe<Scalars['String']['output']>;
  routeId: Scalars['String']['output'];
  routeLongName?: Maybe<Scalars['String']['output']>;
  routeShortName?: Maybe<Scalars['String']['output']>;
  routeSortOrder?: Maybe<Scalars['Int']['output']>;
  routeTextColor?: Maybe<Scalars['String']['output']>;
  routeType?: Maybe<Scalars['Int']['output']>;
  routeUrl?: Maybe<Scalars['String']['output']>;
  /** Derived trip patterns for this route. */
  tripPatterns: Array<TripPattern>;
  /** Every trip that runs on this route. */
  trips: Array<Trip>;
};

/** Aggregate schedule adherence for one route over a date range, from non-null StopPassage.adherenceSec samples. */
export type RouteAdherenceSummary = {
  __typename?: 'RouteAdherenceSummary';
  meanAdherenceSec: Scalars['Float']['output'];
  /** Fraction of samples within the on-time window, from 0 to 1. */
  onTimePct: Scalars['Float']['output'];
  routeId: Scalars['String']['output'];
  routeShortName?: Maybe<Scalars['String']['output']>;
  /** Number of non-null adherence samples; a route with zero samples produces no summary at all. */
  sampleCount: Scalars['Int']['output'];
};

/** The result of diffing two GTFS revisions' derived schedules. */
export type ScheduleComparison = {
  __typename?: 'ScheduleComparison';
  calendarChanges: Array<CalendarChange>;
  /** True if fromRevisionId's derived schedule data is stale (needs a rebuild) at the time of comparison. */
  fromDerivationStale: Scalars['Boolean']['output'];
  fromRevisionId: Scalars['ID']['output'];
  /** Per-route/direction/service headway comparison between the two revisions. */
  headwaySummaries: Array<HeadwaySummary>;
  toDerivationStale: Scalars['Boolean']['output'];
  toRevisionId: Scalars['ID']['output'];
  /** Total number of trip changes found, independent of tripChanges' offset/limit pagination. */
  tripChangeCount: Scalars['Int']['output'];
  /** A page of the trip-level changes, per the query's offset/limit. */
  tripChanges: Array<TripChange>;
};

/** Scheduled timing for one stop of one specific trip, as derived from its stop_times. */
export type ScheduleTime = {
  __typename?: 'ScheduleTime';
  /** Scheduled arrival in seconds since midnight of the trip's service day, or null if not scheduled at this stop. */
  arrivalSec?: Maybe<Scalars['Int']['output']>;
  departureSec?: Maybe<Scalars['Int']['output']>;
  /** True if this time was interpolated (the trip's own stop_times didn't specify it directly) rather than taken as-is from GTFS. */
  interpolated: Scalars['Boolean']['output'];
  schedDwellTimeSec?: Maybe<Scalars['Int']['output']>;
  /** Scheduled travel time from the previous stop, in seconds. */
  schedTravelTimeSec?: Maybe<Scalars['Int']['output']>;
  stopPathIndex: Scalars['Int']['output'];
};

export type SetCalendarExceptionInput = {
  date: Scalars['String']['input'];
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  /** 1 = service added, 2 = service removed, null = delete the exception */
  exceptionType?: InputMaybe<Scalars['Int']['input']>;
  expectedVersion: Scalars['Long']['input'];
  serviceId: Scalars['String']['input'];
};

export type SetCalendarInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  endDate: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  friday: Scalars['Boolean']['input'];
  monday: Scalars['Boolean']['input'];
  saturday: Scalars['Boolean']['input'];
  serviceId: Scalars['String']['input'];
  startDate: Scalars['String']['input'];
  sunday: Scalars['Boolean']['input'];
  thursday: Scalars['Boolean']['input'];
  tuesday: Scalars['Boolean']['input'];
  wednesday: Scalars['Boolean']['input'];
};

export type SetStopDwellInput = {
  draftId: Scalars['ID']['input'];
  /** Desired dwell time at this stop, in seconds. */
  dwellSec: Scalars['Int']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  stopSequence: Scalars['Int']['input'];
  tripId: Scalars['String']['input'];
};

/** Severity of a GTFS validation finding. INFO issues are reported but never block activation or count toward errorCount/warningCount. */
export type Severity =
  | 'ERROR'
  | 'INFO'
  | 'WARNING';

/** GTFS shapes.txt: a trip's physical path, summarized from its points. */
export type Shape = {
  __typename?: 'Shape';
  /** The whole shape as a GeoJSON LineString (coordinates are [lng, lat]), gzip-compressed then base64-encoded; null if it has fewer than two valid points. Prefer this over `points` for drawing. */
  geometry?: Maybe<Scalars['String']['output']>;
  lengthM?: Maybe<Scalars['Float']['output']>;
  pointCount: Scalars['Int']['output'];
  points: Array<ShapePoint>;
  shapeId: Scalars['String']['output'];
};

/** One point along a trip shape's geometry. */
export type ShapePoint = {
  __typename?: 'ShapePoint';
  distTraveled?: Maybe<Scalars['Float']['output']>;
  lat?: Maybe<Scalars['Float']['output']>;
  lon?: Maybe<Scalars['Float']['output']>;
  sequence: Scalars['Int']['output'];
};

export type ShiftTripInput = {
  /** Signed offset in seconds applied to every scheduled stop time on the trip. */
  deltaSec: Scalars['Int']['input'];
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  tripId: Scalars['String']['input'];
};

/**
 * GTFS stops.txt: a physical location vehicles stop at, or a station/entrance grouping such
 * locations. locationType: 0=stop/platform, 1=station, 2=entrance/exit, 3=generic node, 4=boarding
 * area. wheelchairBoarding: 0=unknown, 1=accessible, 2=not accessible.
 */
export type Stop = {
  __typename?: 'Stop';
  /** Stops whose parentStation is this stop. */
  childStops: Array<Stop>;
  level?: Maybe<Level>;
  levelId?: Maybe<Scalars['String']['output']>;
  locationType?: Maybe<Scalars['Int']['output']>;
  parentStation?: Maybe<Scalars['String']['output']>;
  platformCode?: Maybe<Scalars['String']['output']>;
  stopCode?: Maybe<Scalars['String']['output']>;
  stopDesc?: Maybe<Scalars['String']['output']>;
  stopId: Scalars['String']['output'];
  stopLat?: Maybe<Scalars['Float']['output']>;
  stopLon?: Maybe<Scalars['Float']['output']>;
  stopName?: Maybe<Scalars['String']['output']>;
  wheelchairBoarding?: Maybe<Scalars['Int']['output']>;
  zoneId?: Maybe<Scalars['String']['output']>;
};

/** How one scheduled stop of a trip differs between the two compared revisions. */
export type StopChangeKind =
  | 'STOP_ADDED'
  | 'STOP_REMOVED'
  | 'STOP_TIME_CHANGED';

/** One scheduled stop of one trip execution, as derived on-demand from AVL match history. observedSec/adherenceSec are null when the trip never reached this stop (by stopPathIndex) in the queried window. */
export type StopPassage = {
  __typename?: 'StopPassage';
  /** Seconds behind (positive) or ahead of (negative) schedule when observed, or null if never reached. Not guaranteed to equal observedSec minus scheduledSec; see the interpolation note on the underlying match. */
  adherenceSec?: Maybe<Scalars['Int']['output']>;
  blockId?: Maybe<Scalars['String']['output']>;
  /** The trip's GTFS directionId, constant across every passage of this trip execution. */
  direction?: Maybe<Scalars['Int']['output']>;
  /** When the vehicle was actually observed at this stop, in seconds since midnight of serviceDate, or null if never reached. */
  observedSec?: Maybe<Scalars['Int']['output']>;
  /** Occupancy reported by the AVL ping that produced this passage's observedSec, or null if observedSec is null or the feed didn't report occupancy. */
  occupancyStatus?: Maybe<AvlOccupancyStatus>;
  routeId: Scalars['String']['output'];
  /** Scheduled arrival (or departure, if no arrival is scheduled) in seconds since midnight of serviceDate. */
  scheduledSec?: Maybe<Scalars['Int']['output']>;
  /** The calendar service date this trip execution is inferred to belong to, which for a late-night trip may be the day before the AVL activity was observed. */
  serviceDate: Scalars['String']['output'];
  stopId: Scalars['String']['output'];
  stopName?: Maybe<Scalars['String']['output']>;
  /** Index into the trip's stop sequence. */
  stopPathIndex: Scalars['Int']['output'];
  tripId: Scalars['String']['output'];
  /** The vehicle that served this trip execution; shared by every passage in the group even when observedSec is null. */
  vehicleId: Scalars['String']['output'];
};

/** One scheduled stop within a trip pattern, with derived timing and geometry metadata shared by every trip that follows the pattern. */
export type StopPath = {
  __typename?: 'StopPath';
  /** Scheduled break duration at this stop, in seconds, when layoverStop is true. */
  breakTimeSec?: Maybe<Scalars['Int']['output']>;
  dropOffType?: Maybe<Scalars['Int']['output']>;
  /** True if trips pause here for a scheduled break (e.g. a terminal layover) rather than just a brief dwell. */
  layoverStop: Scalars['Boolean']['output'];
  /** Distance in meters along the pattern's shape from the start of the trip to this stop. */
  lengthM: Scalars['Float']['output'];
  /** GTFS pickup_type for this stop across the pattern's trips, if consistent. */
  pickupType?: Maybe<Scalars['Int']['output']>;
  /** True if schedule adherence is meaningfully measured at this stop (e.g. a timepoint). */
  scheduleAdherenceStop: Scalars['Boolean']['output'];
  stop?: Maybe<Stop>;
  stopId: Scalars['String']['output'];
  /** Index of this stop within the pattern's stop sequence; used throughout the API (Vehicle, AvlReport, predictions) to refer to a position along a trip. */
  stopPathIndex: Scalars['Int']['output'];
  /** The original GTFS stop_sequence this stop path was derived from. */
  stopSeq: Scalars['Int']['output'];
  /** Typical observed or scheduled dwell time at this stop, in seconds. */
  typicalDwellTimeSec?: Maybe<Scalars['Int']['output']>;
  /** Typical observed or scheduled travel time from the previous stop, in seconds. */
  typicalTravelTimeSec?: Maybe<Scalars['Int']['output']>;
  /** True if this is a scheduled waiting point (e.g. a layover) rather than a normal pass-through stop. */
  waitStop: Scalars['Boolean']['output'];
};

/** A predicted (and, once reached, actual) arrival/departure at one scheduled stop of a vehicle's current trip. */
export type StopPrediction = {
  __typename?: 'StopPrediction';
  /** ISO-8601 GPS-interpolated arrival, present once the stop has been reached. */
  actualArrival?: Maybe<Scalars['String']['output']>;
  actualDeparture?: Maybe<Scalars['String']['output']>;
  /** Whether the actual crossing meets the configured report-gap quality limit; null when unknown. */
  actualReliable?: Maybe<Scalars['Boolean']['output']>;
  /** Gap in seconds between GPS reports bracketing the actual crossing; null when unknown. */
  actualReportGapSec?: Maybe<Scalars['Int']['output']>;
  /** The algorithm that produced this prediction, or null for a stop already reached (no prediction was needed). */
  algorithm?: Maybe<PredictionAlgorithm>;
  /** Estimated arrival standard deviation in seconds (one SD), not a guaranteed confidence interval. Includes uncertainty of unlearned legs and ageing Kalman state. */
  confidenceSec?: Maybe<Scalars['Int']['output']>;
  /** ISO-8601 predicted arrival, retained after arrival for comparison with the actual. */
  predictedArrival?: Maybe<Scalars['String']['output']>;
  predictedDeparture?: Maybe<Scalars['String']['output']>;
  /** ISO-8601 instant of the scheduled arrival, or null if the trip has no scheduled arrival at this stop. */
  scheduledArrival?: Maybe<Scalars['String']['output']>;
  scheduledDeparture?: Maybe<Scalars['String']['output']>;
  /** ISO-8601 timestamp of the AVL observation used to generate this prediction, independent of processing time. */
  sourceReportTs?: Maybe<Scalars['String']['output']>;
  stop?: Maybe<Stop>;
  /** Index into the trip pattern's stopPaths this prediction is for. */
  stopPathIndex: Scalars['Int']['output'];
};

/**
 * GTFS stop_times.txt: one scheduled stop of one trip. arrivalTime/departureTime are the feed's
 * original HH:MM:SS strings (which may exceed 24:00:00 for a post-midnight trip); the *Seconds
 * fields are the same values normalized to seconds since midnight of the service day, for
 * arithmetic. pickupType/dropOffType: 0=regular, 1=none, 2=phone agency, 3=coordinate with driver.
 * timepoint: 0=approximate, 1=exact (default).
 */
export type StopTime = {
  __typename?: 'StopTime';
  arrivalTime?: Maybe<Scalars['String']['output']>;
  arrivalTimeSeconds?: Maybe<Scalars['Int']['output']>;
  departureTime?: Maybe<Scalars['String']['output']>;
  departureTimeSeconds?: Maybe<Scalars['Int']['output']>;
  dropOffType?: Maybe<Scalars['Int']['output']>;
  pickupType?: Maybe<Scalars['Int']['output']>;
  shapeDistTraveled?: Maybe<Scalars['Float']['output']>;
  stop?: Maybe<Stop>;
  stopHeadsign?: Maybe<Scalars['String']['output']>;
  stopId?: Maybe<Scalars['String']['output']>;
  stopSequence: Scalars['Int']['output'];
  timepoint?: Maybe<Scalars['Int']['output']>;
  tripId: Scalars['String']['output'];
};

/** One scheduled stop's change within a changed trip. */
export type StopTimeChange = {
  __typename?: 'StopTimeChange';
  /** Change in scheduled arrival time in seconds, or null if not applicable. */
  arrivalDeltaSec?: Maybe<Scalars['Int']['output']>;
  departureDeltaSec?: Maybe<Scalars['Int']['output']>;
  kind: StopChangeKind;
  /** The stop involved, or null for a STOP_TIME_CHANGED entry that didn't change which stop is at this sequence. */
  stopId?: Maybe<Scalars['String']['output']>;
  stopSequence: Scalars['Int']['output'];
};

/** GTFS table names supported by the records query, covering the GTFS-Fares v2, GTFS-Flex, translations and attributions extensions. */
export type Table =
  | 'AREAS'
  | 'ATTRIBUTIONS'
  | 'BOOKING_RULES'
  | 'FARE_ATTRIBUTES'
  | 'FARE_LEG_JOIN_RULES'
  | 'FARE_LEG_RULES'
  | 'FARE_MEDIA'
  | 'FARE_PRODUCTS'
  | 'FARE_RULES'
  | 'FARE_TRANSFER_RULES'
  | 'LOCATIONS'
  | 'LOCATION_GROUPS'
  | 'LOCATION_GROUP_STOPS'
  | 'NETWORKS'
  | 'RIDER_CATEGORIES'
  | 'ROUTE_NETWORKS'
  | 'STOP_AREAS'
  | 'TIMEFRAMES'
  | 'TRANSLATIONS';

/** GTFS transfers.txt: a rule describing how a transfer between two stops/routes/trips should be treated. transferType: 0=recommended, 1=timed, 2=requires minimum time, 3=not possible. */
export type Transfer = {
  __typename?: 'Transfer';
  fromRouteId?: Maybe<Scalars['String']['output']>;
  fromStopId?: Maybe<Scalars['String']['output']>;
  fromTripId?: Maybe<Scalars['String']['output']>;
  minTransferTime?: Maybe<Scalars['Int']['output']>;
  toRouteId?: Maybe<Scalars['String']['output']>;
  toStopId?: Maybe<Scalars['String']['output']>;
  toTripId?: Maybe<Scalars['String']['output']>;
  transferType?: Maybe<Scalars['Int']['output']>;
};

/**
 * GTFS trips.txt: one scheduled run of a route. directionId is GTFS's binary direction (0/1,
 * feed-defined meaning, typically outbound/inbound). wheelchairAccessible/bikesAllowed: 0=unknown,
 * 1=yes, 2=no. tripPatternId links to this trip's derived TripPattern, if one was computed.
 */
export type Trip = {
  __typename?: 'Trip';
  bikesAllowed?: Maybe<Scalars['Int']['output']>;
  /** The vehicle block this trip belongs to. */
  block?: Maybe<Block>;
  blockId?: Maybe<Scalars['String']['output']>;
  directionId?: Maybe<Scalars['Int']['output']>;
  endTimeSec?: Maybe<Scalars['Int']['output']>;
  /** True if this trip is generated from a GTFS frequencies.txt entry rather than fixed stop_times. */
  frequencyBased?: Maybe<Scalars['Boolean']['output']>;
  /** True if this trip has no derivable schedule (e.g. frequency-based with no fixed times). */
  noSchedule?: Maybe<Scalars['Boolean']['output']>;
  /** The derived trip pattern this trip follows. */
  pattern?: Maybe<TripPattern>;
  route?: Maybe<Route>;
  routeId: Scalars['String']['output'];
  /** This trip's scheduled stop timings, in stop sequence order. */
  scheduleTimes: Array<ScheduleTime>;
  serviceId: Scalars['String']['output'];
  shape?: Maybe<Shape>;
  shapeId?: Maybe<Scalars['String']['output']>;
  /** Scheduled start of this trip, in seconds since midnight of its service day. */
  startTimeSec?: Maybe<Scalars['Int']['output']>;
  /** This trip's scheduled stops, in stop_sequence order. */
  stopTimes: Array<StopTime>;
  tripHeadsign?: Maybe<Scalars['String']['output']>;
  tripId: Scalars['String']['output'];
  tripPatternId?: Maybe<Scalars['ID']['output']>;
  tripShortName?: Maybe<Scalars['String']['output']>;
  wheelchairAccessible?: Maybe<Scalars['Int']['output']>;
};

/** Aggregate schedule adherence for one trip execution (tripId + serviceDate) over a date range. */
export type TripAdherenceSummary = {
  __typename?: 'TripAdherenceSummary';
  meanAdherenceSec: Scalars['Float']['output'];
  onTimePct: Scalars['Float']['output'];
  routeId: Scalars['String']['output'];
  sampleCount: Scalars['Int']['output'];
  serviceDate: Scalars['String']['output'];
  tripId: Scalars['String']['output'];
};

/** One trip's change between the two compared revisions. */
export type TripChange = {
  __typename?: 'TripChange';
  /** Changed top-level trip fields, keyed by field name to {from, to} old/new value pairs. */
  fieldChanges: Scalars['JSON']['output'];
  kind: TripChangeKind;
  /** Change in the trip's total scheduled run time in seconds (toRevisionId minus fromRevisionId), or null for an added/removed trip. */
  runTimeDeltaSec?: Maybe<Scalars['Int']['output']>;
  /** Per-stop changes within this trip. */
  stopChanges: Array<StopTimeChange>;
  tripId: Scalars['String']['output'];
};

/** How a trip or calendar differs between the two compared revisions. */
export type TripChangeKind =
  /** Present in toRevisionId but not fromRevisionId. */
  | 'ADDED'
  /** Present in both, with at least one changed field. */
  | 'MODIFIED'
  /** Present in fromRevisionId but not toRevisionId. */
  | 'REMOVED';

/** A distinct sequence of stops shared by one or more trips of a route, derived from the GTFS stop_times. Vehicles are matched against patterns rather than individual trips. */
export type TripPattern = {
  __typename?: 'TripPattern';
  directionId?: Maybe<Scalars['Int']['output']>;
  /** Geographic bounding box of this pattern's shape. */
  extent?: Maybe<Extent>;
  /** The pattern's whole polyline as a GeoJSON LineString (coordinates are [lng, lat]), gzip-compressed then base64-encoded; null if the pattern has fewer than two usable points. */
  geometry?: Maybe<Scalars['String']['output']>;
  /** Representative headsign shared by this pattern's trips, if consistent. */
  headsign?: Maybe<Scalars['String']['output']>;
  /** Total pattern length in meters, from its shape. */
  lengthM?: Maybe<Scalars['Float']['output']>;
  /** Stable identifier for this pattern within its revision. */
  patternKey: Scalars['String']['output'];
  route?: Maybe<Route>;
  routeId: Scalars['String']['output'];
  shapeId?: Maybe<Scalars['String']['output']>;
  /** Number of scheduled stops in this pattern. */
  stopCount: Scalars['Int']['output'];
  /** This pattern's scheduled stops, in order. */
  stopPaths: Array<StopPath>;
  /** Number of trips that follow this pattern. */
  tripCount: Scalars['Int']['output'];
  /** Every trip that follows this pattern. */
  trips: Array<Trip>;
};

export type UpdateFeedInput = {
  autoActivate?: InputMaybe<Scalars['Boolean']['input']>;
  description?: InputMaybe<Scalars['String']['input']>;
  enabled?: InputMaybe<Scalars['Boolean']['input']>;
  name: Scalars['String']['input'];
  pollingCron?: InputMaybe<Scalars['String']['input']>;
  url: Scalars['String']['input'];
};

export type UpdateStopTimeInput = {
  /** null clears the value; omitting is the same as null */
  arrivalSec?: InputMaybe<Scalars['Int']['input']>;
  /** null clears the value; omitting is the same as null */
  departureSec?: InputMaybe<Scalars['Int']['input']>;
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  /** The draft's version this edit is based on; used to detect concurrent edits from another editor. */
  expectedVersion: Scalars['Long']['input'];
  stopSequence: Scalars['Int']['input'];
  tripId: Scalars['String']['input'];
};

/**
 * One analyzer finding on a specific GTFS entity (not rolled up per rule): where it is, what was
 * observed versus expected, and how to fix it.
 */
export type ValidationFinding = {
  __typename?: 'ValidationFinding';
  /** Rule-specific context, e.g. distances or speeds. */
  details?: Maybe<Scalars['JSON']['output']>;
  entityId?: Maybe<Scalars['String']['output']>;
  /** Feed, File, Agency, Stop, Route, Trip, Shape, Service, Fare, Transfer, Pathway, Level, ... */
  entityType: Scalars['String']['output'];
  expectedValue?: Maybe<Scalars['String']['output']>;
  field?: Maybe<Scalars['String']['output']>;
  /** Source GTFS file, e.g. stop_times.txt. */
  file?: Maybe<Scalars['String']['output']>;
  /** 1-based row in `file`. */
  line?: Maybe<Scalars['Int']['output']>;
  message: Scalars['String']['output'];
  observedValue?: Maybe<Scalars['String']['output']>;
  remediation: Scalars['String']['output'];
  /** SPEC, INTEROP, QUALITY or ANALYTICS. */
  ruleClass: Scalars['String']['output'];
  ruleId: Scalars['String']['output'];
  /** Set on trip-scoped findings. */
  serviceId?: Maybe<Scalars['String']['output']>;
  severity: FindingSeverity;
  title: Scalars['String']['output'];
};

/** One distinct validation rule violation found in a revision, with a sample affected row. */
export type ValidationNotice = {
  __typename?: 'ValidationNotice';
  /** Number of rows this rule fired on. */
  count: Scalars['Int']['output'];
  /** The validator rule that fired (e.g. a MobilityData GTFS validator notice code). */
  rule: Scalars['String']['output'];
  /** One example of an affected row, for diagnosing the issue. */
  sample: Scalars['String']['output'];
  severity: Severity;
};

/** Aggregate GTFS validation results for one revision. */
export type ValidationSummary = {
  __typename?: 'ValidationSummary';
  errorCount: Scalars['Int']['output'];
  /** Individual validation findings, one per distinct rule/severity combination that fired, with how many rows it affected. */
  notices: Array<ValidationNotice>;
  warningCount: Scalars['Int']['output'];
};

/** A vehicle's latest known position and match state on an AVL feed, updated on every poll. */
export type Vehicle = {
  __typename?: 'Vehicle';
  /** Compass heading in degrees, if reported. */
  bearing?: Maybe<Scalars['Float']['output']>;
  block?: Maybe<Block>;
  /** The stop this vehicle is currently approaching or at, or null while unmatched. */
  currentStop?: Maybe<Stop>;
  /** Distance in meters the vehicle has traveled along its matched trip's shape, or null while unmatched. */
  distanceAlongTripM?: Maybe<Scalars['Float']['output']>;
  /** Operator-facing label for the vehicle, if the feed provides one; often distinct from vehicleId. */
  label?: Maybe<Scalars['String']['output']>;
  /** True once this vehicle has been matched to a trip. */
  matched: Scalars['Boolean']['output'];
  occupancyStatus?: Maybe<AvlOccupancyStatus>;
  pattern?: Maybe<TripPattern>;
  /** Raw reported position. */
  position: LatLon;
  /** ISO-8601 instant this position was reported by the vehicle. */
  reportTs: Scalars['String']['output'];
  /** Seconds behind (positive) or ahead of (negative) schedule at the matched position, or null while unmatched. */
  scheduleAdherenceSec?: Maybe<Scalars['Int']['output']>;
  /** Position snapped onto the matched trip's shape, or null while unmatched. */
  snappedPosition?: Maybe<LatLon>;
  speedMps?: Maybe<Scalars['Float']['output']>;
  /** True if this vehicle's last report is older than the feed considers live; the position shown may no longer reflect where the vehicle actually is. */
  stale: Scalars['Boolean']['output'];
  /** Index into the matched trip pattern's stopPaths the vehicle is currently progressing through, or null while unmatched. */
  stopPathIndex?: Maybe<Scalars['Int']['output']>;
  /** The trip this vehicle is matched to, or null while unmatched. */
  trip?: Maybe<Trip>;
  vehicleId: Scalars['String']['output'];
};

/** GTFS-Realtime VehiclePosition current_status, describing the vehicle's relationship to currentStopSequence. */
export type VehicleStopStatus =
  /** The vehicle is just about to arrive at the stop. */
  | 'INCOMING_AT'
  /** The vehicle has departed and is in transit to the next stop. */
  | 'IN_TRANSIT_TO'
  /** The vehicle is stopped at the stop. */
  | 'STOPPED_AT';

export type RouteAdherenceQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
  startDate: string;
  endDate: string;
}>;


export type RouteAdherenceQuery = { routeAdherence: Array<{ tripId: string, serviceDate: string, stopId: string, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null }> };

export type AllRoutesAdherenceQueryVariables = Exact<{
  feedCode: string;
  startDate: string;
  endDate: string;
}>;


export type AllRoutesAdherenceQuery = { allRoutesAdherence: Array<{ routeId: string, routeShortName: string | null, sampleCount: number, meanAdherenceSec: number, onTimePct: number }> };

export type TripAdherenceQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
  startDate: string;
  endDate: string;
}>;


export type TripAdherenceQuery = { tripAdherence: Array<{ serviceDate: string, stopId: string, stopName: string | null, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null, vehicleId: string, direction: number | null, occupancyStatus: AvlOccupancyStatus | null }> };

export type AllTripsAdherenceQueryVariables = Exact<{
  feedCode: string;
  routeId?: string | null | undefined;
  startDate: string;
  endDate: string;
}>;


export type AllTripsAdherenceQuery = { allTripsAdherence: Array<{ tripId: string, routeId: string, serviceDate: string, sampleCount: number, meanAdherenceSec: number, onTimePct: number }> };

export type StopAdherenceQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  startDate: string;
  endDate: string;
}>;


export type StopAdherenceQuery = { stopAdherence: Array<{ tripId: string, routeId: string, serviceDate: string, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null }> };

export type AgenciesQueryVariables = Exact<{
  feedCode: string;
}>;


export type AgenciesQuery = { agencies: Array<{ agencyId: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AgenciesDetailQueryVariables = Exact<{
  feedCode: string;
}>;


export type AgenciesDetailQuery = { agencies: Array<{ agencyId: string | null, agencyName: string | null, agencyUrl: string | null, agencyTimezone: string | null, agencyPhone: string | null, agencyEmail: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AvlFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type AvlFeedsQuery = { avlFeeds: Array<{ code: string, name: string, gtfsFeedCode: string, format: AvlFormat, predictionAlgorithms: Array<PredictionAlgorithm>, enabled: boolean, pollIntervalSec: number, lastPollAt: string | null, lastPollStatus: string | null, lastPollReportCount: number | null }> };

export type AvlLogQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  limit?: number | null | undefined;
}>;


export type AvlLogQuery = { avlReports: Array<{ ts: string, speedMps: number | null, bearing: number | null, matchStatus: MatchStatus, descTripId: string | null, position: { lat: number, lon: number }, match: { stopPathIndex: number, distanceAlongTripM: number, deviationM: number, scheduleAdherenceSec: number | null, score: number | null, trip: { tripId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null } | null } | null } | null }> };

export type AvlReportCatalogQueryVariables = Exact<{
  feedCode: string;
}>;


export type AvlReportCatalogQuery = { avlReportCatalog: { timeZone: string, vehicleIds: Array<string> } };

export type AvlReportPageQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  date: string;
  startTime: string;
  endTime: string;
  page?: number | null | undefined;
  pageSize?: number | null | undefined;
}>;


export type AvlReportPageQuery = { avlReportPage: { totalCount: number, page: number, pageSize: number, items: Array<{ vehicleId: string, ts: string, processedAt: string, source: AvlFormat, speedMps: number | null, descTripId: string | null, position: { lat: number, lon: number }, match: { trip: { tripId: string } | null } | null }> } };

export type AvlTrailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  limit?: number | null | undefined;
}>;


export type AvlTrailQuery = { avlReports: Array<{ vehicleId: string, ts: string, speedMps: number | null, bearing: number | null, currentStatus: VehicleStopStatus | null, occupancyStatus: AvlOccupancyStatus | null, descTripId: string | null, position: { lat: number, lon: number } }> };

export type BlockDetailQueryVariables = Exact<{
  feedCode: string;
  blockId: string;
  serviceId: string;
}>;


export type BlockDetailQuery = { block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string>, blockTrips: Array<{ listIndex: number, layoverAfterSec: number | null, deadheadAfter: boolean | null, trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } }> } | null };

export type BlocksListQueryVariables = Exact<{
  feedCode: string;
}>;


export type BlocksListQuery = { blocks: Array<{ blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string>, trips: Array<{ routeId: string }> }> };

export type CompareRevisionsQueryVariables = Exact<{
  fromRevisionId: string | number;
  toRevisionId: string | number;
  routeId?: string | null | undefined;
  directionId?: number | null | undefined;
  serviceId?: string | null | undefined;
  offset?: number | null | undefined;
  limit?: number | null | undefined;
}>;


export type CompareRevisionsQuery = { compareRevisions: { fromRevisionId: string, toRevisionId: string, fromDerivationStale: boolean, toDerivationStale: boolean, tripChangeCount: number, tripChanges: Array<{ tripId: string, kind: TripChangeKind, fieldChanges: any, runTimeDeltaSec: number | null, stopChanges: Array<{ stopSequence: number, kind: StopChangeKind, stopId: string | null, arrivalDeltaSec: number | null, departureDeltaSec: number | null }> }>, calendarChanges: Array<{ serviceId: string, kind: CalendarChangeKind, fieldChanges: any, exceptionChanges: Array<{ date: string, kind: ExceptionChangeKind, fromType: number | null, toType: number | null }> }>, headwaySummaries: Array<{ routeId: string, directionId: number | null, serviceId: string, fromTripCount: number, toTripCount: number, fromMeanGapSec: number | null, toMeanGapSec: number | null, fromMaxGapSec: number | null, toMaxGapSec: number | null }> } };

export type DraftEditResultFFragment = { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null };

export type UpdateStopTimeMutationVariables = Exact<{
  input: UpdateStopTimeInput;
}>;


export type UpdateStopTimeMutation = { updateStopTime: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type ShiftTripMutationVariables = Exact<{
  input: ShiftTripInput;
}>;


export type ShiftTripMutation = { shiftTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type SetStopDwellMutationVariables = Exact<{
  input: SetStopDwellInput;
}>;


export type SetStopDwellMutation = { setStopDwell: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type AddTripMutationVariables = Exact<{
  input: AddTripInput;
}>;


export type AddTripMutation = { addTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type DuplicateTripMutationVariables = Exact<{
  input: DuplicateTripInput;
}>;


export type DuplicateTripMutation = { duplicateTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type DeleteTripMutationVariables = Exact<{
  input: DeleteTripInput;
}>;


export type DeleteTripMutation = { deleteTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type BulkShiftTripsMutationVariables = Exact<{
  input: BulkShiftTripsInput;
}>;


export type BulkShiftTripsMutation = { bulkShiftTrips: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type InsertTripStopMutationVariables = Exact<{
  input: InsertTripStopInput;
}>;


export type InsertTripStopMutation = { insertTripStop: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type RemoveTripStopMutationVariables = Exact<{
  input: RemoveTripStopInput;
}>;


export type RemoveTripStopMutation = { removeTripStop: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type ReorderTripStopsMutationVariables = Exact<{
  input: ReorderTripStopsInput;
}>;


export type ReorderTripStopsMutation = { reorderTripStops: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type UndoDraftEditMutationVariables = Exact<{
  id: string | number;
  editor: string;
  expectedVersion: number;
}>;


export type UndoDraftEditMutation = { undoDraftEdit: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type RedoDraftEditMutationVariables = Exact<{
  id: string | number;
  editor: string;
  expectedVersion: number;
}>;


export type RedoDraftEditMutation = { redoDraftEdit: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: any, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type DraftGridQueryVariables = Exact<{
  draftId: string | number;
  routeId: string;
  directionId?: number | null | undefined;
  serviceId?: string | null | undefined;
}>;


export type DraftGridQuery = { draftGrid: { stops: Array<{ stopSequence: number, stopId: string | null, stopName: string | null, timepoint: number | null }>, trips: Array<{ tripId: string, firstDepartureSec: number | null, headsign: string | null, blockId: string | null, directionId: number | null, serviceId: string | null, cells: Array<{ stopSequence: number, arrivalSec: number | null, departureSec: number | null }> }> } };

export type ForkDraftMutationVariables = Exact<{
  input: ForkDraftInput;
}>;


export type ForkDraftMutation = { forkDraft: { id: string, feedCode: string, label: string | null, version: number } };

export type DiscardDraftMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;


export type DiscardDraftMutation = { discardDraft: boolean };

export type RevertDraftToForkMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;


export type RevertDraftToForkMutation = { revertDraftToFork: { id: string, version: number, derivationStale: boolean } };

export type ActivateDraftMutationVariables = Exact<{
  id: string | number;
  editor: string;
  force?: boolean | null | undefined;
}>;


export type ActivateDraftMutation = { activateDraft: { id: string, status: RevisionStatus } };

export type RebuildDraftMutationVariables = Exact<{
  id: string | number;
}>;


export type RebuildDraftMutation = { rebuildDraft: { id: string, state: DraftJobState, phase: DraftJobPhase, error: string | null } };

export type ClaimDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
  takeOver?: boolean | null | undefined;
}>;


export type ClaimDraftEditorMutation = { claimDraftEditor: { editor: string, expiresAt: string } };

export type RenewDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;


export type RenewDraftEditorMutation = { renewDraftEditor: { editor: string, expiresAt: string } };

export type ReleaseDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;


export type ReleaseDraftEditorMutation = { releaseDraftEditor: boolean };

export type DraftPatternsQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
  revisionId: string | number;
}>;


export type DraftPatternsQuery = { tripPatterns: Array<{ patternKey: string, directionId: number | null, headsign: string | null, stopCount: number, stopPaths: Array<{ stopPathIndex: number, stopId: string, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null }> }> };

export type DraftTripDetailQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
  revisionId: string | number;
}>;


export type DraftTripDetailQuery = { trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, serviceId: string, blockId: string | null, shapeId: string | null, startTimeSec: number | null, endTimeSec: number | null, frequencyBased: boolean | null, noSchedule: boolean | null, pattern: { patternKey: string, stopCount: number, lengthM: number | null } | null, block: { blockId: string, serviceId: string } | null } | null };

export type DraftsQueryVariables = Exact<{
  feedCode?: string | null | undefined;
}>;


export type DraftsQuery = { drafts: Array<{ id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: RevisionStatus, kind: DraftKind, version: number, derivationStale: boolean, lastValidation: any, createdBy: string | null, createdAt: string, rowCounts: any, lock: { editor: string, expiresAt: string } | null }> };

export type DraftDetailQueryVariables = Exact<{
  id: string | number;
}>;


export type DraftDetailQuery = { draft: { id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: RevisionStatus, kind: DraftKind, version: number, derivationStale: boolean, lastValidation: any, createdBy: string | null, createdAt: string, rowCounts: any, lock: { editor: string, expiresAt: string } | null } | null };

export type DraftEditsQueryVariables = Exact<{
  id: string | number;
  limit?: number | null | undefined;
}>;


export type DraftEditsQuery = { draftEdits: Array<{ seq: number, op: string, summary: string, appliedAt: string, undone: boolean }> };

export type DraftJobQueryVariables = Exact<{
  jobId: string | number;
}>;


export type DraftJobQuery = { draftJob: { id: string, state: DraftJobState, phase: DraftJobPhase, error: string | null } | null };

export type ExploreCalendarQueryVariables = Exact<{
  feedCode: string;
}>;


export type ExploreCalendarQuery = { calendars: Array<{ serviceId: string, monday: boolean | null, tuesday: boolean | null, wednesday: boolean | null, thursday: boolean | null, friday: boolean | null, saturday: boolean | null, sunday: boolean | null, startDate: string | null, endDate: string | null }>, calendarDates: Array<{ serviceId: string, date: string, exceptionType: number | null }> };

export type ExplorePatternsQueryVariables = Exact<{
  feedCode: string;
}>;


export type ExplorePatternsQuery = { tripPatterns: Array<{ patternKey: string, routeId: string, directionId: number | null, headsign: string | null, tripCount: number, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null, routeType: number | null, agencyId: string | null } | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null }> }> };

export type ExploreRoutesQueryVariables = Exact<{
  feedCode: string;
}>;


export type ExploreRoutesQuery = { routes: Array<{ routeId: string, routeShortName: string | null, routeLongName: string | null, routeType: number | null, routeColor: string | null, routeTextColor: string | null, agencyId: string | null }>, agencies: Array<{ agencyId: string | null, agencyName: string | null }> };

export type ExploreStopsQueryVariables = Exact<{
  feedCode: string;
}>;


export type ExploreStopsQuery = { stops: Array<{ stopId: string, stopName: string | null, stopCode: string | null, stopDesc: string | null, stopLat: number | null, stopLon: number | null, locationType: number | null, parentStation: string | null }> };

export type FeedDetailQueryVariables = Exact<{
  code: string;
}>;


export type FeedDetailQuery = { feed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean, source: FeedSource, activeRevision: { id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, filesPresent: Array<string>, rowCounts: any, validationSummary: { errorCount: number, warningCount: number, notices: Array<{ rule: string, severity: Severity, count: number, sample: string }> } | null } | null, revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, supersededAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, errorMessage: string | null, validationSummary: { errorCount: number, warningCount: number, notices: Array<{ rule: string, severity: Severity, count: number, sample: string }> } | null }> } | null, feedInfo: { feedPublisherName: string | null, feedPublisherUrl: string | null, feedLang: string | null, feedStartDate: string | null, feedEndDate: string | null, feedVersion: string | null, feedContactEmail: string | null, feedContactUrl: string | null } | null };

export type FeedGeometryQueryVariables = Exact<{
  feedCode: string;
}>;


export type FeedGeometryQuery = { feedGeometry: string };

export type IngestFeedMutationVariables = Exact<{
  feedCode: string;
}>;


export type IngestFeedMutation = { ingestFeed: { id: string, status: RevisionStatus } };

export type ActivateRevisionMutationVariables = Exact<{
  revisionId: string | number;
}>;


export type ActivateRevisionMutation = { activateRevision: { id: string, status: RevisionStatus } };

export type DeleteRevisionMutationVariables = Exact<{
  revisionId: string | number;
}>;


export type DeleteRevisionMutation = { deleteRevision: boolean };

export type UpdateFeedMutationVariables = Exact<{
  code: string;
  input: UpdateFeedInput;
}>;


export type UpdateFeedMutation = { updateFeed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean } };

export type FeedReadinessQueryVariables = Exact<{
  code: string;
}>;


export type FeedReadinessQuery = { feed: { code: string, name: string, activeRevision: { id: string } | null, revisions: Array<{ id: string, status: RevisionStatus }> } | null };

export type FeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type FeedsQuery = { feeds: Array<{ code: string, name: string, description: string | null, enabled: boolean, source: FeedSource, activeRevision: { id: string, status: RevisionStatus, feedStartDate: string | null, feedEndDate: string | null } | null }> };

export type HeadwayInfoQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  routeId: string;
  directionId?: number | null | undefined;
  algorithm?: PredictionAlgorithm | null | undefined;
}>;


export type HeadwayInfoQuery = { headway: { stopId: string, routeId: string, directionId: number | null, waitSec: number | null, gapsSec: Array<number>, scheduledHeadwaySec: number | null } };

export type HeadwayRoutePatternsQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;


export type HeadwayRoutePatternsQuery = { tripPatterns: Array<{ patternKey: string, directionId: number | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, stop: { stopName: string | null } | null }> }> };

export type StartOptimizationRunMutationVariables = Exact<{
  input: OptimizationRunInput;
}>;


export type StartOptimizationRunMutation = { startOptimizationRun: { id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null } };

export type ApplyOptimizationRecommendationsMutationVariables = Exact<{
  runId: string | number;
  recommendationIds: Array<string | number> | string | number;
  label?: string | null | undefined;
  editor: string;
}>;


export type ApplyOptimizationRecommendationsMutation = { applyOptimizationRecommendations: { id: string, feedCode: string, label: string | null, status: RevisionStatus, kind: DraftKind } };

export type OptimizationRunsQueryVariables = Exact<{
  feedCode: string;
  limit?: number | null | undefined;
  offset?: number | null | undefined;
}>;


export type OptimizationRunsQuery = { optimizationRuns: Array<{ id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null }> };

export type OptimizationRunQueryVariables = Exact<{
  id: string | number;
}>;


export type OptimizationRunQuery = { optimizationRun: { id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null } | null };

export type OptimizationRecommendationsQueryVariables = Exact<{
  runId: string | number;
  status?: OptimizationRecommendationStatus | null | undefined;
  offset?: number | null | undefined;
  limit?: number | null | undefined;
}>;


export type OptimizationRecommendationsQuery = { optimizationRecommendations: Array<{ id: string, kind: OptimizationRecommendationKind, status: OptimizationRecommendationStatus, sampleCount: number, deltaSec: number, currentValue: any, proposedValue: any, evidence: any, reason: string }> };

export type PredictionAccuracyQueryVariables = Exact<{
  feedCode: string;
  sinceDays?: number | null | undefined;
}>;


export type PredictionAccuracyQuery = { predictionAccuracy: Array<{ algorithm: PredictionAlgorithm, sampleCount: number, meanErrorSec: number | null, meanAbsErrorSec: number | null }> };

export type RevisionsQueryVariables = Exact<{
  feedCode: string;
}>;


export type RevisionsQuery = { revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null }> };

export type RouteDetailQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;


export type RouteDetailQuery = { route: { routeId: string, routeShortName: string | null, routeLongName: string | null, routeDesc: string | null, routeType: number | null, routeColor: string | null, routeTextColor: string | null, agencyId: string | null } | null, tripPatterns: Array<{ patternKey: string, directionId: number | null, headsign: string | null, shapeId: string | null, stopCount: number, tripCount: number, lengthM: number | null, geometry: string | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, lengthM: number, pickupType: number | null, dropOffType: number | null, waitStop: boolean, scheduleAdherenceStop: boolean, layoverStop: boolean, breakTimeSec: number | null, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> }> };

export type RoutesQueryVariables = Exact<{
  feedCode: string;
}>;


export type RoutesQuery = { routes: Array<{ routeId: string, routeShortName: string | null, routeLongName: string | null }> };

export type StopDetailQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
}>;


export type StopDetailQuery = { stop: { stopId: string, stopName: string | null, stopCode: string | null, stopDesc: string | null, stopLat: number | null, stopLon: number | null, zoneId: string | null, locationType: number | null, parentStation: string | null, wheelchairBoarding: number | null, platformCode: string | null } | null };

export type StopBoardQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  routeId?: string | null | undefined;
  directionId?: number | null | undefined;
  algorithm?: PredictionAlgorithm | null | undefined;
}>;


export type StopBoardQuery = { stopPredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null }> };

export type StopsQueryVariables = Exact<{
  feedCode: string;
}>;


export type StopsQuery = { stops: Array<{ stopId: string, stopName: string | null, stopCode: string | null }> };

export type TripDetailQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
}>;


export type TripDetailQuery = { trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, serviceId: string, shapeId: string | null, startTimeSec: number | null, endTimeSec: number | null, frequencyBased: boolean | null, noSchedule: boolean | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, pattern: { patternKey: string, stopCount: number, lengthM: number | null } | null, block: { blockId: string, serviceId: string } | null, scheduleTimes: Array<{ stopPathIndex: number, arrivalSec: number | null, departureSec: number | null, interpolated: boolean, schedTravelTimeSec: number | null, schedDwellTimeSec: number | null }>, stopTimes: Array<{ stopSequence: number, arrivalTime: string | null, departureTime: string | null, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }>, shape: { points: Array<{ lat: number | null, lon: number | null }> } | null } | null };

export type TripsByRouteQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;


export type TripsByRouteQuery = { trips: Array<{ tripId: string, tripHeadsign: string | null, tripShortName: string | null, directionId: number | null, serviceId: string, shapeId: string | null, blockId: string | null, startTimeSec: number | null, endTimeSec: number | null }> };

export type ValidationFindingCountsQueryVariables = Exact<{
  feedCode: string;
  entityType: string;
}>;


export type ValidationFindingCountsQuery = { validationFindingCounts: Array<{ entityId: string, count: number, worstSeverity: FindingSeverity }> };

export type ValidationFindingsQueryVariables = Exact<{
  feedCode: string;
  entityType?: string | null | undefined;
  entityId?: string | null | undefined;
}>;


export type ValidationFindingsQuery = { validationFindings: Array<{ ruleId: string, title: string, severity: FindingSeverity, ruleClass: string, entityType: string, entityId: string | null, serviceId: string | null, file: string | null, line: number | null, field: string | null, observedValue: string | null, expectedValue: string | null, details: any, message: string, remediation: string }> };

export type VehicleDetailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
}>;


export type VehicleDetailQuery = { vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: AvlOccupancyStatus | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null, block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, blockTrips: Array<{ listIndex: number, trip: { tripId: string, tripHeadsign: string | null } }> } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { geometry: string | null } | null } | null, pattern: { patternKey: string, lengthM: number | null, geometry: string | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null, vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, scheduledDeparture: string | null, predictedArrival: string | null, predictedDeparture: string | null, actualArrival: string | null, actualDeparture: string | null, actualReportGapSec: number | null, actualReliable: boolean | null, sourceReportTs: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>, predictionAccuracy: Array<{ algorithm: PredictionAlgorithm, sampleCount: number, meanErrorSec: number | null, meanAbsErrorSec: number | null, eligibleCount: number, predictionCoverage: number, pairedSampleCount: number, p90AbsErrorSec: number | null, p95AbsErrorSec: number | null, confidenceCoverage: number | null, confidenceSampleCount: number }> };

export type VehiclesQueryVariables = Exact<{
  feedCode: string;
  matchedOnly?: boolean | null | undefined;
}>;


export type VehiclesQuery = { vehicles: Array<{ vehicleId: string, label: string | null, reportTs: string, bearing: number | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, speedMps: number | null, position: { lat: number, lon: number }, pattern: { lengthM: number | null } | null, trip: { tripId: string, routeId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } | null, block: { blockId: string, serviceId: string } | null }> };

export const DraftEditResultFFragmentDoc = {"kind":"Document","definitions":[{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<DraftEditResultFFragment, unknown>;
export const RouteAdherenceDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"RouteAdherence"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeAdherence"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"startDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}}},{"kind":"Argument","name":{"kind":"Name","value":"endDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceDate"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledSec"}},{"kind":"Field","name":{"kind":"Name","value":"observedSec"}},{"kind":"Field","name":{"kind":"Name","value":"adherenceSec"}}]}}]}}]} as unknown as DocumentNode<RouteAdherenceQuery, RouteAdherenceQueryVariables>;
export const AllRoutesAdherenceDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AllRoutesAdherence"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"allRoutesAdherence"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"startDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}}},{"kind":"Argument","name":{"kind":"Name","value":"endDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"sampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"meanAdherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"onTimePct"}}]}}]}}]} as unknown as DocumentNode<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>;
export const TripAdherenceDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"TripAdherence"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripAdherence"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"tripId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}}},{"kind":"Argument","name":{"kind":"Name","value":"startDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}}},{"kind":"Argument","name":{"kind":"Name","value":"endDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"serviceDate"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledSec"}},{"kind":"Field","name":{"kind":"Name","value":"observedSec"}},{"kind":"Field","name":{"kind":"Name","value":"adherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"vehicleId"}},{"kind":"Field","name":{"kind":"Name","value":"direction"}},{"kind":"Field","name":{"kind":"Name","value":"occupancyStatus"}}]}}]}}]} as unknown as DocumentNode<TripAdherenceQuery, TripAdherenceQueryVariables>;
export const AllTripsAdherenceDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AllTripsAdherence"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"allTripsAdherence"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"startDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}}},{"kind":"Argument","name":{"kind":"Name","value":"endDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceDate"}},{"kind":"Field","name":{"kind":"Name","value":"sampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"meanAdherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"onTimePct"}}]}}]}}]} as unknown as DocumentNode<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>;
export const StopAdherenceDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"StopAdherence"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopAdherence"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"stopId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}}},{"kind":"Argument","name":{"kind":"Name","value":"startDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startDate"}}},{"kind":"Argument","name":{"kind":"Name","value":"endDate"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endDate"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceDate"}},{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledSec"}},{"kind":"Field","name":{"kind":"Name","value":"observedSec"}},{"kind":"Field","name":{"kind":"Name","value":"adherenceSec"}}]}}]}}]} as unknown as DocumentNode<StopAdherenceQuery, StopAdherenceQueryVariables>;
export const AgenciesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Agencies"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"agencies"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"agencyId"}},{"kind":"Field","name":{"kind":"Name","value":"extent"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"minLat"}},{"kind":"Field","name":{"kind":"Name","value":"minLon"}},{"kind":"Field","name":{"kind":"Name","value":"maxLat"}},{"kind":"Field","name":{"kind":"Name","value":"maxLon"}}]}}]}}]}}]} as unknown as DocumentNode<AgenciesQuery, AgenciesQueryVariables>;
export const AgenciesDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AgenciesDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"agencies"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"agencyId"}},{"kind":"Field","name":{"kind":"Name","value":"agencyName"}},{"kind":"Field","name":{"kind":"Name","value":"agencyUrl"}},{"kind":"Field","name":{"kind":"Name","value":"agencyTimezone"}},{"kind":"Field","name":{"kind":"Name","value":"agencyPhone"}},{"kind":"Field","name":{"kind":"Name","value":"agencyEmail"}},{"kind":"Field","name":{"kind":"Name","value":"extent"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"minLat"}},{"kind":"Field","name":{"kind":"Name","value":"minLon"}},{"kind":"Field","name":{"kind":"Name","value":"maxLat"}},{"kind":"Field","name":{"kind":"Name","value":"maxLon"}}]}}]}}]}}]} as unknown as DocumentNode<AgenciesDetailQuery, AgenciesDetailQueryVariables>;
export const AvlFeedsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AvlFeeds"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"avlFeeds"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"code"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"gtfsFeedCode"}},{"kind":"Field","name":{"kind":"Name","value":"format"}},{"kind":"Field","name":{"kind":"Name","value":"predictionAlgorithms"}},{"kind":"Field","name":{"kind":"Name","value":"enabled"}},{"kind":"Field","name":{"kind":"Name","value":"pollIntervalSec"}},{"kind":"Field","name":{"kind":"Name","value":"lastPollAt"}},{"kind":"Field","name":{"kind":"Name","value":"lastPollStatus"}},{"kind":"Field","name":{"kind":"Name","value":"lastPollReportCount"}}]}}]}}]} as unknown as DocumentNode<AvlFeedsQuery, AvlFeedsQueryVariables>;
export const AvlLogDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AvlLog"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"avlReports"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"vehicleId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"ts"}},{"kind":"Field","name":{"kind":"Name","value":"position"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"speedMps"}},{"kind":"Field","name":{"kind":"Name","value":"bearing"}},{"kind":"Field","name":{"kind":"Name","value":"matchStatus"}},{"kind":"Field","name":{"kind":"Name","value":"descTripId"}},{"kind":"Field","name":{"kind":"Name","value":"match"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"distanceAlongTripM"}},{"kind":"Field","name":{"kind":"Name","value":"deviationM"}},{"kind":"Field","name":{"kind":"Name","value":"scheduleAdherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"score"}},{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}}]}}]}}]}}]}}]}}]} as unknown as DocumentNode<AvlLogQuery, AvlLogQueryVariables>;
export const AvlReportCatalogDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AvlReportCatalog"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"avlReportCatalog"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"timeZone"}},{"kind":"Field","name":{"kind":"Name","value":"vehicleIds"}}]}}]}}]} as unknown as DocumentNode<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>;
export const AvlReportPageDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AvlReportPage"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"date"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"startTime"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"endTime"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"page"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"pageSize"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"avlReportPage"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"vehicleId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}}},{"kind":"Argument","name":{"kind":"Name","value":"date"},"value":{"kind":"Variable","name":{"kind":"Name","value":"date"}}},{"kind":"Argument","name":{"kind":"Name","value":"startTime"},"value":{"kind":"Variable","name":{"kind":"Name","value":"startTime"}}},{"kind":"Argument","name":{"kind":"Name","value":"endTime"},"value":{"kind":"Variable","name":{"kind":"Name","value":"endTime"}}},{"kind":"Argument","name":{"kind":"Name","value":"page"},"value":{"kind":"Variable","name":{"kind":"Name","value":"page"}}},{"kind":"Argument","name":{"kind":"Name","value":"pageSize"},"value":{"kind":"Variable","name":{"kind":"Name","value":"pageSize"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"totalCount"}},{"kind":"Field","name":{"kind":"Name","value":"page"}},{"kind":"Field","name":{"kind":"Name","value":"pageSize"}},{"kind":"Field","name":{"kind":"Name","value":"items"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicleId"}},{"kind":"Field","name":{"kind":"Name","value":"ts"}},{"kind":"Field","name":{"kind":"Name","value":"processedAt"}},{"kind":"Field","name":{"kind":"Name","value":"source"}},{"kind":"Field","name":{"kind":"Name","value":"position"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"speedMps"}},{"kind":"Field","name":{"kind":"Name","value":"descTripId"}},{"kind":"Field","name":{"kind":"Name","value":"match"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}}]}}]}}]}}]}}]}}]} as unknown as DocumentNode<AvlReportPageQuery, AvlReportPageQueryVariables>;
export const AvlTrailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"AvlTrail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"avlReports"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"vehicleId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicleId"}},{"kind":"Field","name":{"kind":"Name","value":"ts"}},{"kind":"Field","name":{"kind":"Name","value":"position"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"speedMps"}},{"kind":"Field","name":{"kind":"Name","value":"bearing"}},{"kind":"Field","name":{"kind":"Name","value":"currentStatus"}},{"kind":"Field","name":{"kind":"Name","value":"occupancyStatus"}},{"kind":"Field","name":{"kind":"Name","value":"descTripId"}}]}}]}}]} as unknown as DocumentNode<AvlTrailQuery, AvlTrailQueryVariables>;
export const BlockDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"BlockDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"blockId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"block"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"blockId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"blockId"}}},{"kind":"Argument","name":{"kind":"Name","value":"serviceId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"tripCount"}},{"kind":"Field","name":{"kind":"Name","value":"routeIds"}},{"kind":"Field","name":{"kind":"Name","value":"blockTrips"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"listIndex"}},{"kind":"Field","name":{"kind":"Name","value":"layoverAfterSec"}},{"kind":"Field","name":{"kind":"Name","value":"deadheadAfter"}},{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}}]}}]}}]}}]}}]}}]} as unknown as DocumentNode<BlockDetailQuery, BlockDetailQueryVariables>;
export const BlocksListDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"BlocksList"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blocks"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"tripCount"}},{"kind":"Field","name":{"kind":"Name","value":"routeIds"}},{"kind":"Field","name":{"kind":"Name","value":"trips"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}}]}}]}}]}}]} as unknown as DocumentNode<BlocksListQuery, BlocksListQueryVariables>;
export const CompareRevisionsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"CompareRevisions"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"fromRevisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"toRevisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"offset"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"compareRevisions"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"fromRevisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"fromRevisionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"toRevisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"toRevisionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"directionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"serviceId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}}},{"kind":"Argument","name":{"kind":"Name","value":"offset"},"value":{"kind":"Variable","name":{"kind":"Name","value":"offset"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"fromRevisionId"}},{"kind":"Field","name":{"kind":"Name","value":"toRevisionId"}},{"kind":"Field","name":{"kind":"Name","value":"fromDerivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"toDerivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"tripChangeCount"}},{"kind":"Field","name":{"kind":"Name","value":"tripChanges"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"fieldChanges"}},{"kind":"Field","name":{"kind":"Name","value":"runTimeDeltaSec"}},{"kind":"Field","name":{"kind":"Name","value":"stopChanges"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopSequence"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"arrivalDeltaSec"}},{"kind":"Field","name":{"kind":"Name","value":"departureDeltaSec"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"calendarChanges"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"fieldChanges"}},{"kind":"Field","name":{"kind":"Name","value":"exceptionChanges"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"date"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"fromType"}},{"kind":"Field","name":{"kind":"Name","value":"toType"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"headwaySummaries"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"fromTripCount"}},{"kind":"Field","name":{"kind":"Name","value":"toTripCount"}},{"kind":"Field","name":{"kind":"Name","value":"fromMeanGapSec"}},{"kind":"Field","name":{"kind":"Name","value":"toMeanGapSec"}},{"kind":"Field","name":{"kind":"Name","value":"fromMaxGapSec"}},{"kind":"Field","name":{"kind":"Name","value":"toMaxGapSec"}}]}}]}}]}}]} as unknown as DocumentNode<CompareRevisionsQuery, CompareRevisionsQueryVariables>;
export const UpdateStopTimeDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"UpdateStopTime"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"UpdateStopTimeInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"updateStopTime"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>;
export const ShiftTripDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ShiftTrip"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ShiftTripInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"shiftTrip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<ShiftTripMutation, ShiftTripMutationVariables>;
export const SetStopDwellDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"SetStopDwell"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"SetStopDwellInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"setStopDwell"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<SetStopDwellMutation, SetStopDwellMutationVariables>;
export const AddTripDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"AddTrip"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"AddTripInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"addTrip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<AddTripMutation, AddTripMutationVariables>;
export const DuplicateTripDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"DuplicateTrip"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"DuplicateTripInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"duplicateTrip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<DuplicateTripMutation, DuplicateTripMutationVariables>;
export const DeleteTripDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"DeleteTrip"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"DeleteTripInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"deleteTrip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<DeleteTripMutation, DeleteTripMutationVariables>;
export const BulkShiftTripsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"BulkShiftTrips"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"BulkShiftTripsInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"bulkShiftTrips"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>;
export const InsertTripStopDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"InsertTripStop"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"InsertTripStopInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"insertTripStop"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<InsertTripStopMutation, InsertTripStopMutationVariables>;
export const RemoveTripStopDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"RemoveTripStop"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"RemoveTripStopInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"removeTripStop"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<RemoveTripStopMutation, RemoveTripStopMutationVariables>;
export const ReorderTripStopsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ReorderTripStops"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ReorderTripStopsInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"reorderTripStops"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>;
export const UndoDraftEditDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"UndoDraftEdit"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"expectedVersion"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"Long"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"undoDraftEdit"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}},{"kind":"Argument","name":{"kind":"Name","value":"expectedVersion"},"value":{"kind":"Variable","name":{"kind":"Name","value":"expectedVersion"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<UndoDraftEditMutation, UndoDraftEditMutationVariables>;
export const RedoDraftEditDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"RedoDraftEdit"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"expectedVersion"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"Long"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"redoDraftEdit"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}},{"kind":"Argument","name":{"kind":"Name","value":"expectedVersion"},"value":{"kind":"Variable","name":{"kind":"Name","value":"expectedVersion"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"FragmentSpread","name":{"kind":"Name","value":"DraftEditResultF"}}]}}]}},{"kind":"FragmentDefinition","name":{"kind":"Name","value":"DraftEditResultF"},"typeCondition":{"kind":"NamedType","name":{"kind":"Name","value":"DraftEditResult"}},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"edit"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}},{"kind":"Field","name":{"kind":"Name","value":"canUndo"}},{"kind":"Field","name":{"kind":"Name","value":"canRedo"}}]}}]} as unknown as DocumentNode<RedoDraftEditMutation, RedoDraftEditMutationVariables>;
export const DraftGridDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftGrid"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"draftId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draftGrid"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"draftId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"draftId"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"directionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"serviceId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"serviceId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stops"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopSequence"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"timepoint"}}]}},{"kind":"Field","name":{"kind":"Name","value":"trips"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"firstDepartureSec"}},{"kind":"Field","name":{"kind":"Name","value":"headsign"}},{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"cells"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopSequence"}},{"kind":"Field","name":{"kind":"Name","value":"arrivalSec"}},{"kind":"Field","name":{"kind":"Name","value":"departureSec"}}]}}]}}]}}]}}]} as unknown as DocumentNode<DraftGridQuery, DraftGridQueryVariables>;
export const ForkDraftDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ForkDraft"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ForkDraftInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"forkDraft"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"feedCode"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"version"}}]}}]}}]} as unknown as DocumentNode<ForkDraftMutation, ForkDraftMutationVariables>;
export const DiscardDraftDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"DiscardDraft"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"discardDraft"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}}]}]}}]} as unknown as DocumentNode<DiscardDraftMutation, DiscardDraftMutationVariables>;
export const RevertDraftToForkDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"RevertDraftToFork"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"revertDraftToFork"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}}]}}]}}]} as unknown as DocumentNode<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>;
export const ActivateDraftDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ActivateDraft"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"force"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Boolean"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"activateDraft"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}},{"kind":"Argument","name":{"kind":"Name","value":"force"},"value":{"kind":"Variable","name":{"kind":"Name","value":"force"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}}]}}]}}]} as unknown as DocumentNode<ActivateDraftMutation, ActivateDraftMutationVariables>;
export const RebuildDraftDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"RebuildDraft"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"rebuildDraft"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"state"}},{"kind":"Field","name":{"kind":"Name","value":"phase"}},{"kind":"Field","name":{"kind":"Name","value":"error"}}]}}]}}]} as unknown as DocumentNode<RebuildDraftMutation, RebuildDraftMutationVariables>;
export const ClaimDraftEditorDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ClaimDraftEditor"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"takeOver"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Boolean"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"claimDraftEditor"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}},{"kind":"Argument","name":{"kind":"Name","value":"takeOver"},"value":{"kind":"Variable","name":{"kind":"Name","value":"takeOver"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}}]} as unknown as DocumentNode<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>;
export const RenewDraftEditorDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"RenewDraftEditor"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"renewDraftEditor"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}}]}}]} as unknown as DocumentNode<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>;
export const ReleaseDraftEditorDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ReleaseDraftEditor"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"releaseDraftEditor"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}}]}]}}]} as unknown as DocumentNode<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>;
export const DraftPatternsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftPatterns"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripPatterns"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"revisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"headsign"}},{"kind":"Field","name":{"kind":"Name","value":"stopCount"}},{"kind":"Field","name":{"kind":"Name","value":"stopPaths"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"typicalTravelTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"typicalDwellTimeSec"}}]}}]}}]}}]} as unknown as DocumentNode<DraftPatternsQuery, DraftPatternsQueryVariables>;
export const DraftTripDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftTripDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"trip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"tripId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}}},{"kind":"Argument","name":{"kind":"Name","value":"revisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"shapeId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"frequencyBased"}},{"kind":"Field","name":{"kind":"Name","value":"noSchedule"}},{"kind":"Field","name":{"kind":"Name","value":"pattern"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"stopCount"}},{"kind":"Field","name":{"kind":"Name","value":"lengthM"}}]}},{"kind":"Field","name":{"kind":"Name","value":"block"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}}]}}]}}]}}]} as unknown as DocumentNode<DraftTripDetailQuery, DraftTripDetailQueryVariables>;
export const DraftsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Drafts"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"drafts"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"feedCode"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"baseRevisionId"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}},{"kind":"Field","name":{"kind":"Name","value":"createdBy"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"rowCounts"}}]}}]}}]} as unknown as DocumentNode<DraftsQuery, DraftsQueryVariables>;
export const DraftDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draft"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"feedCode"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"baseRevisionId"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"version"}},{"kind":"Field","name":{"kind":"Name","value":"derivationStale"}},{"kind":"Field","name":{"kind":"Name","value":"lastValidation"}},{"kind":"Field","name":{"kind":"Name","value":"lock"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"editor"}},{"kind":"Field","name":{"kind":"Name","value":"expiresAt"}}]}},{"kind":"Field","name":{"kind":"Name","value":"createdBy"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"rowCounts"}}]}}]}}]} as unknown as DocumentNode<DraftDetailQuery, DraftDetailQueryVariables>;
export const DraftEditsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftEdits"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draftEdits"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"seq"}},{"kind":"Field","name":{"kind":"Name","value":"op"}},{"kind":"Field","name":{"kind":"Name","value":"summary"}},{"kind":"Field","name":{"kind":"Name","value":"appliedAt"}},{"kind":"Field","name":{"kind":"Name","value":"undone"}}]}}]}}]} as unknown as DocumentNode<DraftEditsQuery, DraftEditsQueryVariables>;
export const DraftJobDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"DraftJob"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"jobId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"draftJob"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"jobId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"jobId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"state"}},{"kind":"Field","name":{"kind":"Name","value":"phase"}},{"kind":"Field","name":{"kind":"Name","value":"error"}}]}}]}}]} as unknown as DocumentNode<DraftJobQuery, DraftJobQueryVariables>;
export const ExploreCalendarDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ExploreCalendar"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"calendars"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"monday"}},{"kind":"Field","name":{"kind":"Name","value":"tuesday"}},{"kind":"Field","name":{"kind":"Name","value":"wednesday"}},{"kind":"Field","name":{"kind":"Name","value":"thursday"}},{"kind":"Field","name":{"kind":"Name","value":"friday"}},{"kind":"Field","name":{"kind":"Name","value":"saturday"}},{"kind":"Field","name":{"kind":"Name","value":"sunday"}},{"kind":"Field","name":{"kind":"Name","value":"startDate"}},{"kind":"Field","name":{"kind":"Name","value":"endDate"}}]}},{"kind":"Field","name":{"kind":"Name","value":"calendarDates"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"date"}},{"kind":"Field","name":{"kind":"Name","value":"exceptionType"}}]}}]}}]} as unknown as DocumentNode<ExploreCalendarQuery, ExploreCalendarQueryVariables>;
export const ExplorePatternsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ExplorePatterns"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripPatterns"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"headsign"}},{"kind":"Field","name":{"kind":"Name","value":"tripCount"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeType"}},{"kind":"Field","name":{"kind":"Name","value":"agencyId"}}]}},{"kind":"Field","name":{"kind":"Name","value":"stopPaths"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"typicalTravelTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"typicalDwellTimeSec"}}]}}]}}]}}]} as unknown as DocumentNode<ExplorePatternsQuery, ExplorePatternsQueryVariables>;
export const ExploreRoutesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ExploreRoutes"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routes"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}},{"kind":"Field","name":{"kind":"Name","value":"routeType"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}},{"kind":"Field","name":{"kind":"Name","value":"agencyId"}}]}},{"kind":"Field","name":{"kind":"Name","value":"agencies"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"agencyId"}},{"kind":"Field","name":{"kind":"Name","value":"agencyName"}}]}}]}}]} as unknown as DocumentNode<ExploreRoutesQuery, ExploreRoutesQueryVariables>;
export const ExploreStopsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ExploreStops"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stops"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopCode"}},{"kind":"Field","name":{"kind":"Name","value":"stopDesc"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}},{"kind":"Field","name":{"kind":"Name","value":"locationType"}},{"kind":"Field","name":{"kind":"Name","value":"parentStation"}}]}}]}}]} as unknown as DocumentNode<ExploreStopsQuery, ExploreStopsQueryVariables>;
export const FeedDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"FeedDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"code"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"feed"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"code"},"value":{"kind":"Variable","name":{"kind":"Name","value":"code"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"code"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"description"}},{"kind":"Field","name":{"kind":"Name","value":"url"}},{"kind":"Field","name":{"kind":"Name","value":"pollingCron"}},{"kind":"Field","name":{"kind":"Name","value":"enabled"}},{"kind":"Field","name":{"kind":"Name","value":"source"}},{"kind":"Field","name":{"kind":"Name","value":"activeRevision"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"activatedAt"}},{"kind":"Field","name":{"kind":"Name","value":"feedStartDate"}},{"kind":"Field","name":{"kind":"Name","value":"feedEndDate"}},{"kind":"Field","name":{"kind":"Name","value":"byteSize"}},{"kind":"Field","name":{"kind":"Name","value":"contentSha256"}},{"kind":"Field","name":{"kind":"Name","value":"filesPresent"}},{"kind":"Field","name":{"kind":"Name","value":"rowCounts"}},{"kind":"Field","name":{"kind":"Name","value":"validationSummary"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"errorCount"}},{"kind":"Field","name":{"kind":"Name","value":"warningCount"}},{"kind":"Field","name":{"kind":"Name","value":"notices"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"rule"}},{"kind":"Field","name":{"kind":"Name","value":"severity"}},{"kind":"Field","name":{"kind":"Name","value":"count"}},{"kind":"Field","name":{"kind":"Name","value":"sample"}}]}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"revisions"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"activatedAt"}},{"kind":"Field","name":{"kind":"Name","value":"supersededAt"}},{"kind":"Field","name":{"kind":"Name","value":"feedStartDate"}},{"kind":"Field","name":{"kind":"Name","value":"feedEndDate"}},{"kind":"Field","name":{"kind":"Name","value":"byteSize"}},{"kind":"Field","name":{"kind":"Name","value":"contentSha256"}},{"kind":"Field","name":{"kind":"Name","value":"errorMessage"}},{"kind":"Field","name":{"kind":"Name","value":"validationSummary"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"errorCount"}},{"kind":"Field","name":{"kind":"Name","value":"warningCount"}},{"kind":"Field","name":{"kind":"Name","value":"notices"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"rule"}},{"kind":"Field","name":{"kind":"Name","value":"severity"}},{"kind":"Field","name":{"kind":"Name","value":"count"}},{"kind":"Field","name":{"kind":"Name","value":"sample"}}]}}]}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"feedInfo"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"code"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"feedPublisherName"}},{"kind":"Field","name":{"kind":"Name","value":"feedPublisherUrl"}},{"kind":"Field","name":{"kind":"Name","value":"feedLang"}},{"kind":"Field","name":{"kind":"Name","value":"feedStartDate"}},{"kind":"Field","name":{"kind":"Name","value":"feedEndDate"}},{"kind":"Field","name":{"kind":"Name","value":"feedVersion"}},{"kind":"Field","name":{"kind":"Name","value":"feedContactEmail"}},{"kind":"Field","name":{"kind":"Name","value":"feedContactUrl"}}]}}]}}]} as unknown as DocumentNode<FeedDetailQuery, FeedDetailQueryVariables>;
export const FeedGeometryDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"FeedGeometry"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"feedGeometry"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}]}]}}]} as unknown as DocumentNode<FeedGeometryQuery, FeedGeometryQueryVariables>;
export const IngestFeedDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"IngestFeed"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"ingestFeed"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}}]}}]}}]} as unknown as DocumentNode<IngestFeedMutation, IngestFeedMutationVariables>;
export const ActivateRevisionDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ActivateRevision"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"activateRevision"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"revisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}}]}}]}}]} as unknown as DocumentNode<ActivateRevisionMutation, ActivateRevisionMutationVariables>;
export const DeleteRevisionDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"DeleteRevision"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"deleteRevision"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"revisionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"revisionId"}}}]}]}}]} as unknown as DocumentNode<DeleteRevisionMutation, DeleteRevisionMutationVariables>;
export const UpdateFeedDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"UpdateFeed"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"code"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"UpdateFeedInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"updateFeed"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"code"},"value":{"kind":"Variable","name":{"kind":"Name","value":"code"}}},{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"code"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"description"}},{"kind":"Field","name":{"kind":"Name","value":"url"}},{"kind":"Field","name":{"kind":"Name","value":"pollingCron"}},{"kind":"Field","name":{"kind":"Name","value":"enabled"}}]}}]}}]} as unknown as DocumentNode<UpdateFeedMutation, UpdateFeedMutationVariables>;
export const FeedReadinessDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"FeedReadiness"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"code"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"feed"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"code"},"value":{"kind":"Variable","name":{"kind":"Name","value":"code"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"code"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"activeRevision"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}}]}},{"kind":"Field","name":{"kind":"Name","value":"revisions"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}}]}}]}}]}}]} as unknown as DocumentNode<FeedReadinessQuery, FeedReadinessQueryVariables>;
export const FeedsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Feeds"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"feeds"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"code"}},{"kind":"Field","name":{"kind":"Name","value":"name"}},{"kind":"Field","name":{"kind":"Name","value":"description"}},{"kind":"Field","name":{"kind":"Name","value":"enabled"}},{"kind":"Field","name":{"kind":"Name","value":"source"}},{"kind":"Field","name":{"kind":"Name","value":"activeRevision"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"feedStartDate"}},{"kind":"Field","name":{"kind":"Name","value":"feedEndDate"}}]}}]}}]}}]} as unknown as DocumentNode<FeedsQuery, FeedsQueryVariables>;
export const HeadwayInfoDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"HeadwayInfo"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"algorithm"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"PredictionAlgorithm"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"headway"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"stopId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"directionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"algorithm"},"value":{"kind":"Variable","name":{"kind":"Name","value":"algorithm"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"waitSec"}},{"kind":"Field","name":{"kind":"Name","value":"gapsSec"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledHeadwaySec"}}]}}]}}]} as unknown as DocumentNode<HeadwayInfoQuery, HeadwayInfoQueryVariables>;
export const HeadwayRoutePatternsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"HeadwayRoutePatterns"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripPatterns"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"stopPaths"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopName"}}]}}]}}]}}]}}]} as unknown as DocumentNode<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>;
export const StartOptimizationRunDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"StartOptimizationRun"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"input"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"OptimizationRunInput"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"startOptimizationRun"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"input"},"value":{"kind":"Variable","name":{"kind":"Name","value":"input"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"revisionId"}},{"kind":"Field","name":{"kind":"Name","value":"state"}},{"kind":"Field","name":{"kind":"Name","value":"error"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"completedAt"}}]}}]}}]} as unknown as DocumentNode<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>;
export const ApplyOptimizationRecommendationsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"mutation","name":{"kind":"Name","value":"ApplyOptimizationRecommendations"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"runId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"recommendationIds"}},"type":{"kind":"NonNullType","type":{"kind":"ListType","type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"label"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"editor"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"applyOptimizationRecommendations"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"runId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"runId"}}},{"kind":"Argument","name":{"kind":"Name","value":"recommendationIds"},"value":{"kind":"Variable","name":{"kind":"Name","value":"recommendationIds"}}},{"kind":"Argument","name":{"kind":"Name","value":"label"},"value":{"kind":"Variable","name":{"kind":"Name","value":"label"}}},{"kind":"Argument","name":{"kind":"Name","value":"editor"},"value":{"kind":"Variable","name":{"kind":"Name","value":"editor"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"feedCode"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}}]}}]}}]} as unknown as DocumentNode<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>;
export const OptimizationRunsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"OptimizationRuns"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"offset"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"optimizationRuns"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}},{"kind":"Argument","name":{"kind":"Name","value":"offset"},"value":{"kind":"Variable","name":{"kind":"Name","value":"offset"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"revisionId"}},{"kind":"Field","name":{"kind":"Name","value":"state"}},{"kind":"Field","name":{"kind":"Name","value":"error"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"completedAt"}}]}}]}}]} as unknown as DocumentNode<OptimizationRunsQuery, OptimizationRunsQueryVariables>;
export const OptimizationRunDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"OptimizationRun"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"id"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"optimizationRun"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"id"},"value":{"kind":"Variable","name":{"kind":"Name","value":"id"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"revisionId"}},{"kind":"Field","name":{"kind":"Name","value":"state"}},{"kind":"Field","name":{"kind":"Name","value":"error"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"completedAt"}}]}}]}}]} as unknown as DocumentNode<OptimizationRunQuery, OptimizationRunQueryVariables>;
export const OptimizationRecommendationsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"OptimizationRecommendations"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"runId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"ID"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"status"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"OptimizationRecommendationStatus"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"offset"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"limit"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"optimizationRecommendations"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"runId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"runId"}}},{"kind":"Argument","name":{"kind":"Name","value":"status"},"value":{"kind":"Variable","name":{"kind":"Name","value":"status"}}},{"kind":"Argument","name":{"kind":"Name","value":"offset"},"value":{"kind":"Variable","name":{"kind":"Name","value":"offset"}}},{"kind":"Argument","name":{"kind":"Name","value":"limit"},"value":{"kind":"Variable","name":{"kind":"Name","value":"limit"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"kind"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"sampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"deltaSec"}},{"kind":"Field","name":{"kind":"Name","value":"currentValue"}},{"kind":"Field","name":{"kind":"Name","value":"proposedValue"}},{"kind":"Field","name":{"kind":"Name","value":"evidence"}},{"kind":"Field","name":{"kind":"Name","value":"reason"}}]}}]}}]} as unknown as DocumentNode<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>;
export const PredictionAccuracyDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"PredictionAccuracy"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"sinceDays"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"predictionAccuracy"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"sinceDays"},"value":{"kind":"Variable","name":{"kind":"Name","value":"sinceDays"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"algorithm"}},{"kind":"Field","name":{"kind":"Name","value":"sampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"meanErrorSec"}},{"kind":"Field","name":{"kind":"Name","value":"meanAbsErrorSec"}}]}}]}}]} as unknown as DocumentNode<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>;
export const RevisionsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Revisions"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"revisions"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"id"}},{"kind":"Field","name":{"kind":"Name","value":"status"}},{"kind":"Field","name":{"kind":"Name","value":"createdAt"}},{"kind":"Field","name":{"kind":"Name","value":"activatedAt"}}]}}]}}]} as unknown as DocumentNode<RevisionsQuery, RevisionsQueryVariables>;
export const RouteDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"RouteDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"route"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}},{"kind":"Field","name":{"kind":"Name","value":"routeDesc"}},{"kind":"Field","name":{"kind":"Name","value":"routeType"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}},{"kind":"Field","name":{"kind":"Name","value":"agencyId"}}]}},{"kind":"Field","name":{"kind":"Name","value":"tripPatterns"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"headsign"}},{"kind":"Field","name":{"kind":"Name","value":"shapeId"}},{"kind":"Field","name":{"kind":"Name","value":"stopCount"}},{"kind":"Field","name":{"kind":"Name","value":"tripCount"}},{"kind":"Field","name":{"kind":"Name","value":"lengthM"}},{"kind":"Field","name":{"kind":"Name","value":"geometry"}},{"kind":"Field","name":{"kind":"Name","value":"stopPaths"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"lengthM"}},{"kind":"Field","name":{"kind":"Name","value":"pickupType"}},{"kind":"Field","name":{"kind":"Name","value":"dropOffType"}},{"kind":"Field","name":{"kind":"Name","value":"waitStop"}},{"kind":"Field","name":{"kind":"Name","value":"scheduleAdherenceStop"}},{"kind":"Field","name":{"kind":"Name","value":"layoverStop"}},{"kind":"Field","name":{"kind":"Name","value":"breakTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"typicalTravelTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"typicalDwellTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"stop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}}]}}]}}]}}]}}]} as unknown as DocumentNode<RouteDetailQuery, RouteDetailQueryVariables>;
export const RoutesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Routes"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routes"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}}]}}]}}]} as unknown as DocumentNode<RoutesQuery, RoutesQueryVariables>;
export const StopDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"StopDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stop"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"stopId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopCode"}},{"kind":"Field","name":{"kind":"Name","value":"stopDesc"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}},{"kind":"Field","name":{"kind":"Name","value":"zoneId"}},{"kind":"Field","name":{"kind":"Name","value":"locationType"}},{"kind":"Field","name":{"kind":"Name","value":"parentStation"}},{"kind":"Field","name":{"kind":"Name","value":"wheelchairBoarding"}},{"kind":"Field","name":{"kind":"Name","value":"platformCode"}}]}}]}}]} as unknown as DocumentNode<StopDetailQuery, StopDetailQueryVariables>;
export const StopBoardDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"StopBoard"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Int"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"algorithm"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"PredictionAlgorithm"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPredictions"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"stopId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"stopId"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}},{"kind":"Argument","name":{"kind":"Name","value":"directionId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"directionId"}}},{"kind":"Argument","name":{"kind":"Name","value":"algorithm"},"value":{"kind":"Variable","name":{"kind":"Name","value":"algorithm"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledArrival"}},{"kind":"Field","name":{"kind":"Name","value":"predictedArrival"}},{"kind":"Field","name":{"kind":"Name","value":"actualArrival"}},{"kind":"Field","name":{"kind":"Name","value":"algorithm"}},{"kind":"Field","name":{"kind":"Name","value":"confidenceSec"}}]}}]}}]} as unknown as DocumentNode<StopBoardQuery, StopBoardQueryVariables>;
export const StopsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Stops"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stops"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopCode"}}]}}]}}]} as unknown as DocumentNode<StopsQuery, StopsQueryVariables>;
export const TripDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"TripDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"trip"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"tripId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"tripId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"shapeId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"frequencyBased"}},{"kind":"Field","name":{"kind":"Name","value":"noSchedule"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}}]}},{"kind":"Field","name":{"kind":"Name","value":"pattern"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"stopCount"}},{"kind":"Field","name":{"kind":"Name","value":"lengthM"}}]}},{"kind":"Field","name":{"kind":"Name","value":"block"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}}]}},{"kind":"Field","name":{"kind":"Name","value":"scheduleTimes"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"arrivalSec"}},{"kind":"Field","name":{"kind":"Name","value":"departureSec"}},{"kind":"Field","name":{"kind":"Name","value":"interpolated"}},{"kind":"Field","name":{"kind":"Name","value":"schedTravelTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"schedDwellTimeSec"}}]}},{"kind":"Field","name":{"kind":"Name","value":"stopTimes"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopSequence"}},{"kind":"Field","name":{"kind":"Name","value":"arrivalTime"}},{"kind":"Field","name":{"kind":"Name","value":"departureTime"}},{"kind":"Field","name":{"kind":"Name","value":"stop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"shape"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"points"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}}]}}]}}]}}]} as unknown as DocumentNode<TripDetailQuery, TripDetailQueryVariables>;
export const TripsByRouteDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"TripsByRoute"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"trips"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"routeId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"routeId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"tripShortName"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"shapeId"}},{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}}]}}]}}]} as unknown as DocumentNode<TripsByRouteQuery, TripsByRouteQueryVariables>;
export const ValidationFindingCountsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ValidationFindingCounts"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"entityType"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"validationFindingCounts"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"entityType"},"value":{"kind":"Variable","name":{"kind":"Name","value":"entityType"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"entityId"}},{"kind":"Field","name":{"kind":"Name","value":"count"}},{"kind":"Field","name":{"kind":"Name","value":"worstSeverity"}}]}}]}}]} as unknown as DocumentNode<ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables>;
export const ValidationFindingsDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"ValidationFindings"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"entityType"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"entityId"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"validationFindings"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"entityType"},"value":{"kind":"Variable","name":{"kind":"Name","value":"entityType"}}},{"kind":"Argument","name":{"kind":"Name","value":"entityId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"entityId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"ruleId"}},{"kind":"Field","name":{"kind":"Name","value":"title"}},{"kind":"Field","name":{"kind":"Name","value":"severity"}},{"kind":"Field","name":{"kind":"Name","value":"ruleClass"}},{"kind":"Field","name":{"kind":"Name","value":"entityType"}},{"kind":"Field","name":{"kind":"Name","value":"entityId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"file"}},{"kind":"Field","name":{"kind":"Name","value":"line"}},{"kind":"Field","name":{"kind":"Name","value":"field"}},{"kind":"Field","name":{"kind":"Name","value":"observedValue"}},{"kind":"Field","name":{"kind":"Name","value":"expectedValue"}},{"kind":"Field","name":{"kind":"Name","value":"details"}},{"kind":"Field","name":{"kind":"Name","value":"message"}},{"kind":"Field","name":{"kind":"Name","value":"remediation"}}]}}]}}]} as unknown as DocumentNode<ValidationFindingsQuery, ValidationFindingsQueryVariables>;
export const VehicleDetailDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"VehicleDetail"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicle"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"vehicleId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicleId"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"reportTs"}},{"kind":"Field","name":{"kind":"Name","value":"position"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"snappedPosition"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"bearing"}},{"kind":"Field","name":{"kind":"Name","value":"speedMps"}},{"kind":"Field","name":{"kind":"Name","value":"occupancyStatus"}},{"kind":"Field","name":{"kind":"Name","value":"matched"}},{"kind":"Field","name":{"kind":"Name","value":"stale"}},{"kind":"Field","name":{"kind":"Name","value":"scheduleAdherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"distanceAlongTripM"}},{"kind":"Field","name":{"kind":"Name","value":"currentStop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"block"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"blockTrips"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"listIndex"}},{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}}]}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"directionId"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"startTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"endTimeSec"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeLongName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}}]}},{"kind":"Field","name":{"kind":"Name","value":"shape"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"geometry"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"pattern"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"patternKey"}},{"kind":"Field","name":{"kind":"Name","value":"lengthM"}},{"kind":"Field","name":{"kind":"Name","value":"geometry"}},{"kind":"Field","name":{"kind":"Name","value":"stopPaths"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"waitStop"}},{"kind":"Field","name":{"kind":"Name","value":"stop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}},{"kind":"Field","name":{"kind":"Name","value":"stopLat"}},{"kind":"Field","name":{"kind":"Name","value":"stopLon"}}]}}]}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"vehiclePredictions"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"vehicleId"},"value":{"kind":"Variable","name":{"kind":"Name","value":"vehicleId"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"stop"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"stopId"}},{"kind":"Field","name":{"kind":"Name","value":"stopName"}}]}},{"kind":"Field","name":{"kind":"Name","value":"scheduledArrival"}},{"kind":"Field","name":{"kind":"Name","value":"scheduledDeparture"}},{"kind":"Field","name":{"kind":"Name","value":"predictedArrival"}},{"kind":"Field","name":{"kind":"Name","value":"predictedDeparture"}},{"kind":"Field","name":{"kind":"Name","value":"actualArrival"}},{"kind":"Field","name":{"kind":"Name","value":"actualDeparture"}},{"kind":"Field","name":{"kind":"Name","value":"actualReportGapSec"}},{"kind":"Field","name":{"kind":"Name","value":"actualReliable"}},{"kind":"Field","name":{"kind":"Name","value":"sourceReportTs"}},{"kind":"Field","name":{"kind":"Name","value":"algorithm"}},{"kind":"Field","name":{"kind":"Name","value":"confidenceSec"}}]}},{"kind":"Field","name":{"kind":"Name","value":"predictionAccuracy"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"algorithm"}},{"kind":"Field","name":{"kind":"Name","value":"sampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"meanErrorSec"}},{"kind":"Field","name":{"kind":"Name","value":"meanAbsErrorSec"}},{"kind":"Field","name":{"kind":"Name","value":"eligibleCount"}},{"kind":"Field","name":{"kind":"Name","value":"predictionCoverage"}},{"kind":"Field","name":{"kind":"Name","value":"pairedSampleCount"}},{"kind":"Field","name":{"kind":"Name","value":"p90AbsErrorSec"}},{"kind":"Field","name":{"kind":"Name","value":"p95AbsErrorSec"}},{"kind":"Field","name":{"kind":"Name","value":"confidenceCoverage"}},{"kind":"Field","name":{"kind":"Name","value":"confidenceSampleCount"}}]}}]}}]} as unknown as DocumentNode<VehicleDetailQuery, VehicleDetailQueryVariables>;
export const VehiclesDocument = {"kind":"Document","definitions":[{"kind":"OperationDefinition","operation":"query","name":{"kind":"Name","value":"Vehicles"},"variableDefinitions":[{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}},"type":{"kind":"NonNullType","type":{"kind":"NamedType","name":{"kind":"Name","value":"String"}}}},{"kind":"VariableDefinition","variable":{"kind":"Variable","name":{"kind":"Name","value":"matchedOnly"}},"type":{"kind":"NamedType","name":{"kind":"Name","value":"Boolean"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicles"},"arguments":[{"kind":"Argument","name":{"kind":"Name","value":"feedCode"},"value":{"kind":"Variable","name":{"kind":"Name","value":"feedCode"}}},{"kind":"Argument","name":{"kind":"Name","value":"matchedOnly"},"value":{"kind":"Variable","name":{"kind":"Name","value":"matchedOnly"}}}],"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"vehicleId"}},{"kind":"Field","name":{"kind":"Name","value":"label"}},{"kind":"Field","name":{"kind":"Name","value":"reportTs"}},{"kind":"Field","name":{"kind":"Name","value":"position"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lat"}},{"kind":"Field","name":{"kind":"Name","value":"lon"}}]}},{"kind":"Field","name":{"kind":"Name","value":"bearing"}},{"kind":"Field","name":{"kind":"Name","value":"matched"}},{"kind":"Field","name":{"kind":"Name","value":"stale"}},{"kind":"Field","name":{"kind":"Name","value":"scheduleAdherenceSec"}},{"kind":"Field","name":{"kind":"Name","value":"stopPathIndex"}},{"kind":"Field","name":{"kind":"Name","value":"distanceAlongTripM"}},{"kind":"Field","name":{"kind":"Name","value":"speedMps"}},{"kind":"Field","name":{"kind":"Name","value":"pattern"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"lengthM"}}]}},{"kind":"Field","name":{"kind":"Name","value":"trip"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"tripId"}},{"kind":"Field","name":{"kind":"Name","value":"routeId"}},{"kind":"Field","name":{"kind":"Name","value":"tripHeadsign"}},{"kind":"Field","name":{"kind":"Name","value":"route"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"routeShortName"}},{"kind":"Field","name":{"kind":"Name","value":"routeColor"}},{"kind":"Field","name":{"kind":"Name","value":"routeTextColor"}}]}}]}},{"kind":"Field","name":{"kind":"Name","value":"block"},"selectionSet":{"kind":"SelectionSet","selections":[{"kind":"Field","name":{"kind":"Name","value":"blockId"}},{"kind":"Field","name":{"kind":"Name","value":"serviceId"}}]}}]}}]}}]} as unknown as DocumentNode<VehiclesQuery, VehiclesQueryVariables>;

export function useRouteAdherenceQuery(...[options]: {} extends RouteAdherenceQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<RouteAdherenceQuery, RouteAdherenceQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<RouteAdherenceQuery, RouteAdherenceQueryVariables>>]) {
  return useQuery<RouteAdherenceQuery, RouteAdherenceQueryVariables>(RouteAdherenceDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<RouteAdherenceQuery, RouteAdherenceQueryVariables>>)
}
export function useAllRoutesAdherenceQuery(...[options]: {} extends AllRoutesAdherenceQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>>]) {
  return useQuery<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>(AllRoutesAdherenceDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>>)
}
export function useTripAdherenceQuery(...[options]: {} extends TripAdherenceQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<TripAdherenceQuery, TripAdherenceQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<TripAdherenceQuery, TripAdherenceQueryVariables>>]) {
  return useQuery<TripAdherenceQuery, TripAdherenceQueryVariables>(TripAdherenceDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<TripAdherenceQuery, TripAdherenceQueryVariables>>)
}
export function useAllTripsAdherenceQuery(...[options]: {} extends AllTripsAdherenceQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>>]) {
  return useQuery<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>(AllTripsAdherenceDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>>)
}
export function useStopAdherenceQuery(...[options]: {} extends StopAdherenceQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<StopAdherenceQuery, StopAdherenceQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<StopAdherenceQuery, StopAdherenceQueryVariables>>]) {
  return useQuery<StopAdherenceQuery, StopAdherenceQueryVariables>(StopAdherenceDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<StopAdherenceQuery, StopAdherenceQueryVariables>>)
}
export function useAgenciesQuery(...[options]: {} extends AgenciesQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AgenciesQuery, AgenciesQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AgenciesQuery, AgenciesQueryVariables>>]) {
  return useQuery<AgenciesQuery, AgenciesQueryVariables>(AgenciesDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AgenciesQuery, AgenciesQueryVariables>>)
}
export function useAgenciesDetailQuery(...[options]: {} extends AgenciesDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AgenciesDetailQuery, AgenciesDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AgenciesDetailQuery, AgenciesDetailQueryVariables>>]) {
  return useQuery<AgenciesDetailQuery, AgenciesDetailQueryVariables>(AgenciesDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AgenciesDetailQuery, AgenciesDetailQueryVariables>>)
}
export function useAvlFeedsQuery(...[options]: {} extends AvlFeedsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AvlFeedsQuery, AvlFeedsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AvlFeedsQuery, AvlFeedsQueryVariables>>]) {
  return useQuery<AvlFeedsQuery, AvlFeedsQueryVariables>(AvlFeedsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AvlFeedsQuery, AvlFeedsQueryVariables>>)
}
export function useAvlLogQuery(...[options]: {} extends AvlLogQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AvlLogQuery, AvlLogQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AvlLogQuery, AvlLogQueryVariables>>]) {
  return useQuery<AvlLogQuery, AvlLogQueryVariables>(AvlLogDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AvlLogQuery, AvlLogQueryVariables>>)
}
export function useAvlReportCatalogQuery(...[options]: {} extends AvlReportCatalogQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>>]) {
  return useQuery<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>(AvlReportCatalogDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>>)
}
export function useAvlReportPageQuery(...[options]: {} extends AvlReportPageQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AvlReportPageQuery, AvlReportPageQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AvlReportPageQuery, AvlReportPageQueryVariables>>]) {
  return useQuery<AvlReportPageQuery, AvlReportPageQueryVariables>(AvlReportPageDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AvlReportPageQuery, AvlReportPageQueryVariables>>)
}
export function useAvlTrailQuery(...[options]: {} extends AvlTrailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<AvlTrailQuery, AvlTrailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<AvlTrailQuery, AvlTrailQueryVariables>>]) {
  return useQuery<AvlTrailQuery, AvlTrailQueryVariables>(AvlTrailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<AvlTrailQuery, AvlTrailQueryVariables>>)
}
export function useBlockDetailQuery(...[options]: {} extends BlockDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<BlockDetailQuery, BlockDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<BlockDetailQuery, BlockDetailQueryVariables>>]) {
  return useQuery<BlockDetailQuery, BlockDetailQueryVariables>(BlockDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<BlockDetailQuery, BlockDetailQueryVariables>>)
}
export function useBlocksListQuery(...[options]: {} extends BlocksListQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<BlocksListQuery, BlocksListQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<BlocksListQuery, BlocksListQueryVariables>>]) {
  return useQuery<BlocksListQuery, BlocksListQueryVariables>(BlocksListDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<BlocksListQuery, BlocksListQueryVariables>>)
}
export function useCompareRevisionsQuery(...[options]: {} extends CompareRevisionsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<CompareRevisionsQuery, CompareRevisionsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<CompareRevisionsQuery, CompareRevisionsQueryVariables>>]) {
  return useQuery<CompareRevisionsQuery, CompareRevisionsQueryVariables>(CompareRevisionsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<CompareRevisionsQuery, CompareRevisionsQueryVariables>>)
}
export function useUpdateStopTimeMutation(options?: useMutation.Options<UpdateStopTimeMutation, UpdateStopTimeMutationVariables> | (() => useMutation.Options<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>)) {
  return useMutation<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>(UpdateStopTimeDocument, options)
}
export function useShiftTripMutation(options?: useMutation.Options<ShiftTripMutation, ShiftTripMutationVariables> | (() => useMutation.Options<ShiftTripMutation, ShiftTripMutationVariables>)) {
  return useMutation<ShiftTripMutation, ShiftTripMutationVariables>(ShiftTripDocument, options)
}
export function useSetStopDwellMutation(options?: useMutation.Options<SetStopDwellMutation, SetStopDwellMutationVariables> | (() => useMutation.Options<SetStopDwellMutation, SetStopDwellMutationVariables>)) {
  return useMutation<SetStopDwellMutation, SetStopDwellMutationVariables>(SetStopDwellDocument, options)
}
export function useAddTripMutation(options?: useMutation.Options<AddTripMutation, AddTripMutationVariables> | (() => useMutation.Options<AddTripMutation, AddTripMutationVariables>)) {
  return useMutation<AddTripMutation, AddTripMutationVariables>(AddTripDocument, options)
}
export function useDuplicateTripMutation(options?: useMutation.Options<DuplicateTripMutation, DuplicateTripMutationVariables> | (() => useMutation.Options<DuplicateTripMutation, DuplicateTripMutationVariables>)) {
  return useMutation<DuplicateTripMutation, DuplicateTripMutationVariables>(DuplicateTripDocument, options)
}
export function useDeleteTripMutation(options?: useMutation.Options<DeleteTripMutation, DeleteTripMutationVariables> | (() => useMutation.Options<DeleteTripMutation, DeleteTripMutationVariables>)) {
  return useMutation<DeleteTripMutation, DeleteTripMutationVariables>(DeleteTripDocument, options)
}
export function useBulkShiftTripsMutation(options?: useMutation.Options<BulkShiftTripsMutation, BulkShiftTripsMutationVariables> | (() => useMutation.Options<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>)) {
  return useMutation<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>(BulkShiftTripsDocument, options)
}
export function useInsertTripStopMutation(options?: useMutation.Options<InsertTripStopMutation, InsertTripStopMutationVariables> | (() => useMutation.Options<InsertTripStopMutation, InsertTripStopMutationVariables>)) {
  return useMutation<InsertTripStopMutation, InsertTripStopMutationVariables>(InsertTripStopDocument, options)
}
export function useRemoveTripStopMutation(options?: useMutation.Options<RemoveTripStopMutation, RemoveTripStopMutationVariables> | (() => useMutation.Options<RemoveTripStopMutation, RemoveTripStopMutationVariables>)) {
  return useMutation<RemoveTripStopMutation, RemoveTripStopMutationVariables>(RemoveTripStopDocument, options)
}
export function useReorderTripStopsMutation(options?: useMutation.Options<ReorderTripStopsMutation, ReorderTripStopsMutationVariables> | (() => useMutation.Options<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>)) {
  return useMutation<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>(ReorderTripStopsDocument, options)
}
export function useUndoDraftEditMutation(options?: useMutation.Options<UndoDraftEditMutation, UndoDraftEditMutationVariables> | (() => useMutation.Options<UndoDraftEditMutation, UndoDraftEditMutationVariables>)) {
  return useMutation<UndoDraftEditMutation, UndoDraftEditMutationVariables>(UndoDraftEditDocument, options)
}
export function useRedoDraftEditMutation(options?: useMutation.Options<RedoDraftEditMutation, RedoDraftEditMutationVariables> | (() => useMutation.Options<RedoDraftEditMutation, RedoDraftEditMutationVariables>)) {
  return useMutation<RedoDraftEditMutation, RedoDraftEditMutationVariables>(RedoDraftEditDocument, options)
}
export function useDraftGridQuery(...[options]: {} extends DraftGridQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftGridQuery, DraftGridQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftGridQuery, DraftGridQueryVariables>>]) {
  return useQuery<DraftGridQuery, DraftGridQueryVariables>(DraftGridDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftGridQuery, DraftGridQueryVariables>>)
}
export function useForkDraftMutation(options?: useMutation.Options<ForkDraftMutation, ForkDraftMutationVariables> | (() => useMutation.Options<ForkDraftMutation, ForkDraftMutationVariables>)) {
  return useMutation<ForkDraftMutation, ForkDraftMutationVariables>(ForkDraftDocument, options)
}
export function useDiscardDraftMutation(options?: useMutation.Options<DiscardDraftMutation, DiscardDraftMutationVariables> | (() => useMutation.Options<DiscardDraftMutation, DiscardDraftMutationVariables>)) {
  return useMutation<DiscardDraftMutation, DiscardDraftMutationVariables>(DiscardDraftDocument, options)
}
export function useRevertDraftToForkMutation(options?: useMutation.Options<RevertDraftToForkMutation, RevertDraftToForkMutationVariables> | (() => useMutation.Options<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>)) {
  return useMutation<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>(RevertDraftToForkDocument, options)
}
export function useActivateDraftMutation(options?: useMutation.Options<ActivateDraftMutation, ActivateDraftMutationVariables> | (() => useMutation.Options<ActivateDraftMutation, ActivateDraftMutationVariables>)) {
  return useMutation<ActivateDraftMutation, ActivateDraftMutationVariables>(ActivateDraftDocument, options)
}
export function useRebuildDraftMutation(options?: useMutation.Options<RebuildDraftMutation, RebuildDraftMutationVariables> | (() => useMutation.Options<RebuildDraftMutation, RebuildDraftMutationVariables>)) {
  return useMutation<RebuildDraftMutation, RebuildDraftMutationVariables>(RebuildDraftDocument, options)
}
export function useClaimDraftEditorMutation(options?: useMutation.Options<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables> | (() => useMutation.Options<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>)) {
  return useMutation<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>(ClaimDraftEditorDocument, options)
}
export function useRenewDraftEditorMutation(options?: useMutation.Options<RenewDraftEditorMutation, RenewDraftEditorMutationVariables> | (() => useMutation.Options<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>)) {
  return useMutation<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>(RenewDraftEditorDocument, options)
}
export function useReleaseDraftEditorMutation(options?: useMutation.Options<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables> | (() => useMutation.Options<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>)) {
  return useMutation<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>(ReleaseDraftEditorDocument, options)
}
export function useDraftPatternsQuery(...[options]: {} extends DraftPatternsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftPatternsQuery, DraftPatternsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftPatternsQuery, DraftPatternsQueryVariables>>]) {
  return useQuery<DraftPatternsQuery, DraftPatternsQueryVariables>(DraftPatternsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftPatternsQuery, DraftPatternsQueryVariables>>)
}
export function useDraftTripDetailQuery(...[options]: {} extends DraftTripDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftTripDetailQuery, DraftTripDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftTripDetailQuery, DraftTripDetailQueryVariables>>]) {
  return useQuery<DraftTripDetailQuery, DraftTripDetailQueryVariables>(DraftTripDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftTripDetailQuery, DraftTripDetailQueryVariables>>)
}
export function useDraftsQuery(...[options]: {} extends DraftsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftsQuery, DraftsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftsQuery, DraftsQueryVariables>>]) {
  return useQuery<DraftsQuery, DraftsQueryVariables>(DraftsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftsQuery, DraftsQueryVariables>>)
}
export function useDraftDetailQuery(...[options]: {} extends DraftDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftDetailQuery, DraftDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftDetailQuery, DraftDetailQueryVariables>>]) {
  return useQuery<DraftDetailQuery, DraftDetailQueryVariables>(DraftDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftDetailQuery, DraftDetailQueryVariables>>)
}
export function useDraftEditsQuery(...[options]: {} extends DraftEditsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftEditsQuery, DraftEditsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftEditsQuery, DraftEditsQueryVariables>>]) {
  return useQuery<DraftEditsQuery, DraftEditsQueryVariables>(DraftEditsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftEditsQuery, DraftEditsQueryVariables>>)
}
export function useDraftJobQuery(...[options]: {} extends DraftJobQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<DraftJobQuery, DraftJobQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<DraftJobQuery, DraftJobQueryVariables>>]) {
  return useQuery<DraftJobQuery, DraftJobQueryVariables>(DraftJobDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<DraftJobQuery, DraftJobQueryVariables>>)
}
export function useExploreCalendarQuery(...[options]: {} extends ExploreCalendarQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ExploreCalendarQuery, ExploreCalendarQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ExploreCalendarQuery, ExploreCalendarQueryVariables>>]) {
  return useQuery<ExploreCalendarQuery, ExploreCalendarQueryVariables>(ExploreCalendarDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ExploreCalendarQuery, ExploreCalendarQueryVariables>>)
}
export function useExplorePatternsQuery(...[options]: {} extends ExplorePatternsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ExplorePatternsQuery, ExplorePatternsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ExplorePatternsQuery, ExplorePatternsQueryVariables>>]) {
  return useQuery<ExplorePatternsQuery, ExplorePatternsQueryVariables>(ExplorePatternsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ExplorePatternsQuery, ExplorePatternsQueryVariables>>)
}
export function useExploreRoutesQuery(...[options]: {} extends ExploreRoutesQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ExploreRoutesQuery, ExploreRoutesQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ExploreRoutesQuery, ExploreRoutesQueryVariables>>]) {
  return useQuery<ExploreRoutesQuery, ExploreRoutesQueryVariables>(ExploreRoutesDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ExploreRoutesQuery, ExploreRoutesQueryVariables>>)
}
export function useExploreStopsQuery(...[options]: {} extends ExploreStopsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ExploreStopsQuery, ExploreStopsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ExploreStopsQuery, ExploreStopsQueryVariables>>]) {
  return useQuery<ExploreStopsQuery, ExploreStopsQueryVariables>(ExploreStopsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ExploreStopsQuery, ExploreStopsQueryVariables>>)
}
export function useFeedDetailQuery(...[options]: {} extends FeedDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<FeedDetailQuery, FeedDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<FeedDetailQuery, FeedDetailQueryVariables>>]) {
  return useQuery<FeedDetailQuery, FeedDetailQueryVariables>(FeedDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<FeedDetailQuery, FeedDetailQueryVariables>>)
}
export function useFeedGeometryQuery(...[options]: {} extends FeedGeometryQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<FeedGeometryQuery, FeedGeometryQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<FeedGeometryQuery, FeedGeometryQueryVariables>>]) {
  return useQuery<FeedGeometryQuery, FeedGeometryQueryVariables>(FeedGeometryDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<FeedGeometryQuery, FeedGeometryQueryVariables>>)
}
export function useIngestFeedMutation(options?: useMutation.Options<IngestFeedMutation, IngestFeedMutationVariables> | (() => useMutation.Options<IngestFeedMutation, IngestFeedMutationVariables>)) {
  return useMutation<IngestFeedMutation, IngestFeedMutationVariables>(IngestFeedDocument, options)
}
export function useActivateRevisionMutation(options?: useMutation.Options<ActivateRevisionMutation, ActivateRevisionMutationVariables> | (() => useMutation.Options<ActivateRevisionMutation, ActivateRevisionMutationVariables>)) {
  return useMutation<ActivateRevisionMutation, ActivateRevisionMutationVariables>(ActivateRevisionDocument, options)
}
export function useDeleteRevisionMutation(options?: useMutation.Options<DeleteRevisionMutation, DeleteRevisionMutationVariables> | (() => useMutation.Options<DeleteRevisionMutation, DeleteRevisionMutationVariables>)) {
  return useMutation<DeleteRevisionMutation, DeleteRevisionMutationVariables>(DeleteRevisionDocument, options)
}
export function useUpdateFeedMutation(options?: useMutation.Options<UpdateFeedMutation, UpdateFeedMutationVariables> | (() => useMutation.Options<UpdateFeedMutation, UpdateFeedMutationVariables>)) {
  return useMutation<UpdateFeedMutation, UpdateFeedMutationVariables>(UpdateFeedDocument, options)
}
export function useFeedReadinessQuery(...[options]: {} extends FeedReadinessQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<FeedReadinessQuery, FeedReadinessQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<FeedReadinessQuery, FeedReadinessQueryVariables>>]) {
  return useQuery<FeedReadinessQuery, FeedReadinessQueryVariables>(FeedReadinessDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<FeedReadinessQuery, FeedReadinessQueryVariables>>)
}
export function useFeedsQuery(...[options]: {} extends FeedsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<FeedsQuery, FeedsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<FeedsQuery, FeedsQueryVariables>>]) {
  return useQuery<FeedsQuery, FeedsQueryVariables>(FeedsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<FeedsQuery, FeedsQueryVariables>>)
}
export function useHeadwayInfoQuery(...[options]: {} extends HeadwayInfoQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<HeadwayInfoQuery, HeadwayInfoQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<HeadwayInfoQuery, HeadwayInfoQueryVariables>>]) {
  return useQuery<HeadwayInfoQuery, HeadwayInfoQueryVariables>(HeadwayInfoDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<HeadwayInfoQuery, HeadwayInfoQueryVariables>>)
}
export function useHeadwayRoutePatternsQuery(...[options]: {} extends HeadwayRoutePatternsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>>]) {
  return useQuery<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>(HeadwayRoutePatternsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>>)
}
export function useStartOptimizationRunMutation(options?: useMutation.Options<StartOptimizationRunMutation, StartOptimizationRunMutationVariables> | (() => useMutation.Options<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>)) {
  return useMutation<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>(StartOptimizationRunDocument, options)
}
export function useApplyOptimizationRecommendationsMutation(options?: useMutation.Options<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables> | (() => useMutation.Options<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>)) {
  return useMutation<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>(ApplyOptimizationRecommendationsDocument, options)
}
export function useOptimizationRunsQuery(...[options]: {} extends OptimizationRunsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<OptimizationRunsQuery, OptimizationRunsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<OptimizationRunsQuery, OptimizationRunsQueryVariables>>]) {
  return useQuery<OptimizationRunsQuery, OptimizationRunsQueryVariables>(OptimizationRunsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<OptimizationRunsQuery, OptimizationRunsQueryVariables>>)
}
export function useOptimizationRunQuery(...[options]: {} extends OptimizationRunQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<OptimizationRunQuery, OptimizationRunQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<OptimizationRunQuery, OptimizationRunQueryVariables>>]) {
  return useQuery<OptimizationRunQuery, OptimizationRunQueryVariables>(OptimizationRunDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<OptimizationRunQuery, OptimizationRunQueryVariables>>)
}
export function useOptimizationRecommendationsQuery(...[options]: {} extends OptimizationRecommendationsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>>]) {
  return useQuery<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>(OptimizationRecommendationsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>>)
}
export function usePredictionAccuracyQuery(...[options]: {} extends PredictionAccuracyQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>>]) {
  return useQuery<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>(PredictionAccuracyDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>>)
}
export function useRevisionsQuery(...[options]: {} extends RevisionsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<RevisionsQuery, RevisionsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<RevisionsQuery, RevisionsQueryVariables>>]) {
  return useQuery<RevisionsQuery, RevisionsQueryVariables>(RevisionsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<RevisionsQuery, RevisionsQueryVariables>>)
}
export function useRouteDetailQuery(...[options]: {} extends RouteDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<RouteDetailQuery, RouteDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<RouteDetailQuery, RouteDetailQueryVariables>>]) {
  return useQuery<RouteDetailQuery, RouteDetailQueryVariables>(RouteDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<RouteDetailQuery, RouteDetailQueryVariables>>)
}
export function useRoutesQuery(...[options]: {} extends RoutesQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<RoutesQuery, RoutesQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<RoutesQuery, RoutesQueryVariables>>]) {
  return useQuery<RoutesQuery, RoutesQueryVariables>(RoutesDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<RoutesQuery, RoutesQueryVariables>>)
}
export function useStopDetailQuery(...[options]: {} extends StopDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<StopDetailQuery, StopDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<StopDetailQuery, StopDetailQueryVariables>>]) {
  return useQuery<StopDetailQuery, StopDetailQueryVariables>(StopDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<StopDetailQuery, StopDetailQueryVariables>>)
}
export function useStopBoardQuery(...[options]: {} extends StopBoardQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<StopBoardQuery, StopBoardQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<StopBoardQuery, StopBoardQueryVariables>>]) {
  return useQuery<StopBoardQuery, StopBoardQueryVariables>(StopBoardDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<StopBoardQuery, StopBoardQueryVariables>>)
}
export function useStopsQuery(...[options]: {} extends StopsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<StopsQuery, StopsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<StopsQuery, StopsQueryVariables>>]) {
  return useQuery<StopsQuery, StopsQueryVariables>(StopsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<StopsQuery, StopsQueryVariables>>)
}
export function useTripDetailQuery(...[options]: {} extends TripDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<TripDetailQuery, TripDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<TripDetailQuery, TripDetailQueryVariables>>]) {
  return useQuery<TripDetailQuery, TripDetailQueryVariables>(TripDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<TripDetailQuery, TripDetailQueryVariables>>)
}
export function useTripsByRouteQuery(...[options]: {} extends TripsByRouteQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<TripsByRouteQuery, TripsByRouteQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<TripsByRouteQuery, TripsByRouteQueryVariables>>]) {
  return useQuery<TripsByRouteQuery, TripsByRouteQueryVariables>(TripsByRouteDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<TripsByRouteQuery, TripsByRouteQueryVariables>>)
}
export function useValidationFindingCountsQuery(...[options]: {} extends ValidationFindingCountsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables>>]) {
  return useQuery<ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables>(ValidationFindingCountsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables>>)
}
export function useValidationFindingsQuery(...[options]: {} extends ValidationFindingsQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<ValidationFindingsQuery, ValidationFindingsQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<ValidationFindingsQuery, ValidationFindingsQueryVariables>>]) {
  return useQuery<ValidationFindingsQuery, ValidationFindingsQueryVariables>(ValidationFindingsDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<ValidationFindingsQuery, ValidationFindingsQueryVariables>>)
}
export function useVehicleDetailQuery(...[options]: {} extends VehicleDetailQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<VehicleDetailQuery, VehicleDetailQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<VehicleDetailQuery, VehicleDetailQueryVariables>>]) {
  return useQuery<VehicleDetailQuery, VehicleDetailQueryVariables>(VehicleDetailDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<VehicleDetailQuery, VehicleDetailQueryVariables>>)
}
export function useVehiclesQuery(...[options]: {} extends VehiclesQueryVariables ? [options?: MaybeRefOrGetter<useQuery.Options<VehiclesQuery, VehiclesQueryVariables>>] : [options: MaybeRefOrGetter<useQuery.Options<VehiclesQuery, VehiclesQueryVariables>>]) {
  return useQuery<VehiclesQuery, VehiclesQueryVariables>(VehiclesDocument, (options ?? {}) as MaybeRefOrGetter<useQuery.Options<VehiclesQuery, VehiclesQueryVariables>>)
}
