/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import gql from 'graphql-tag';
import * as VueApolloComposable from '@vue/apollo-composable';
import * as VueCompositionApi from '@vue/composition-api';
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
export type ReactiveFunction<TParam> = () => TParam;
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
  /** This stop path's segment of the pattern's shape geometry. */
  pathGeometry?: Maybe<Scalars['JSON']['output']>;
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


export type FeedGeometryQuery = { tripPatterns: Array<{ patternKey: string, routeId: string, headsign: string | null, tripCount: number, route: { routeId: string, routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeType: number | null } | null, stopPaths: Array<{ stopPathIndex: number, pathGeometry: any }> }>, stops: Array<{ stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null, locationType: number | null }> };

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


export type RouteDetailQuery = { route: { routeId: string, routeShortName: string | null, routeLongName: string | null, routeDesc: string | null, routeType: number | null, routeColor: string | null, routeTextColor: string | null, agencyId: string | null } | null, tripPatterns: Array<{ patternKey: string, directionId: number | null, headsign: string | null, shapeId: string | null, stopCount: number, tripCount: number, lengthM: number | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, lengthM: number, pathGeometry: any, pickupType: number | null, dropOffType: number | null, waitStop: boolean, scheduleAdherenceStop: boolean, layoverStop: boolean, breakTimeSec: number | null, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> }> };

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

export type VehicleDetailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
}>;


export type VehicleDetailQuery = { vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: AvlOccupancyStatus | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null, block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, blockTrips: Array<{ listIndex: number, trip: { tripId: string, tripHeadsign: string | null } }> } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { points: Array<{ lat: number | null, lon: number | null }> } | null } | null, pattern: { patternKey: string, lengthM: number | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, pathGeometry: any, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null, vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, scheduledDeparture: string | null, predictedArrival: string | null, predictedDeparture: string | null, actualArrival: string | null, actualDeparture: string | null, actualReportGapSec: number | null, actualReliable: boolean | null, sourceReportTs: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>, predictionAccuracy: Array<{ algorithm: PredictionAlgorithm, sampleCount: number, meanErrorSec: number | null, meanAbsErrorSec: number | null, eligibleCount: number, predictionCoverage: number, pairedSampleCount: number, p90AbsErrorSec: number | null, p95AbsErrorSec: number | null, confidenceCoverage: number | null, confidenceSampleCount: number }> };

export type VehiclesQueryVariables = Exact<{
  feedCode: string;
  matchedOnly?: boolean | null | undefined;
}>;


export type VehiclesQuery = { vehicles: Array<{ vehicleId: string, label: string | null, reportTs: string, bearing: number | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, speedMps: number | null, position: { lat: number, lon: number }, pattern: { lengthM: number | null } | null, trip: { routeId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } | null }> };

export const DraftEditResultFFragmentDoc = gql`
    fragment DraftEditResultF on DraftEditResult {
  draft {
    id
    version
    derivationStale
    lastValidation
    lock {
      editor
      expiresAt
    }
  }
  edit {
    seq
    op
    summary
    appliedAt
    undone
  }
  canUndo
  canRedo
}
    `;
export const RouteAdherenceDocument = gql`
    query RouteAdherence($feedCode: String!, $routeId: String!, $startDate: String!, $endDate: String!) {
  routeAdherence(
    feedCode: $feedCode
    routeId: $routeId
    startDate: $startDate
    endDate: $endDate
  ) {
    tripId
    serviceDate
    stopId
    stopPathIndex
    scheduledSec
    observedSec
    adherenceSec
  }
}
    `;

/**
 * __useRouteAdherenceQuery__
 *
 * To run a query within a Vue component, call `useRouteAdherenceQuery` and pass it any options that fit your needs.
 * When your component renders, `useRouteAdherenceQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useRouteAdherenceQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 *   startDate: // value for 'startDate'
 *   endDate: // value for 'endDate'
 * });
 */
export function useRouteAdherenceQuery(variables: RouteAdherenceQueryVariables | VueCompositionApi.Ref<RouteAdherenceQueryVariables> | ReactiveFunction<RouteAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<RouteAdherenceQuery, RouteAdherenceQueryVariables>(RouteAdherenceDocument, variables, options);
}
export function useRouteAdherenceLazyQuery(variables?: RouteAdherenceQueryVariables | VueCompositionApi.Ref<RouteAdherenceQueryVariables> | ReactiveFunction<RouteAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RouteAdherenceQuery, RouteAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<RouteAdherenceQuery, RouteAdherenceQueryVariables>(RouteAdherenceDocument, variables, options);
}
export type RouteAdherenceQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<RouteAdherenceQuery, RouteAdherenceQueryVariables>;
export const AllRoutesAdherenceDocument = gql`
    query AllRoutesAdherence($feedCode: String!, $startDate: String!, $endDate: String!) {
  allRoutesAdherence(
    feedCode: $feedCode
    startDate: $startDate
    endDate: $endDate
  ) {
    routeId
    routeShortName
    sampleCount
    meanAdherenceSec
    onTimePct
  }
}
    `;

/**
 * __useAllRoutesAdherenceQuery__
 *
 * To run a query within a Vue component, call `useAllRoutesAdherenceQuery` and pass it any options that fit your needs.
 * When your component renders, `useAllRoutesAdherenceQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAllRoutesAdherenceQuery({
 *   feedCode: // value for 'feedCode'
 *   startDate: // value for 'startDate'
 *   endDate: // value for 'endDate'
 * });
 */
export function useAllRoutesAdherenceQuery(variables: AllRoutesAdherenceQueryVariables | VueCompositionApi.Ref<AllRoutesAdherenceQueryVariables> | ReactiveFunction<AllRoutesAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>(AllRoutesAdherenceDocument, variables, options);
}
export function useAllRoutesAdherenceLazyQuery(variables?: AllRoutesAdherenceQueryVariables | VueCompositionApi.Ref<AllRoutesAdherenceQueryVariables> | ReactiveFunction<AllRoutesAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>(AllRoutesAdherenceDocument, variables, options);
}
export type AllRoutesAdherenceQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AllRoutesAdherenceQuery, AllRoutesAdherenceQueryVariables>;
export const TripAdherenceDocument = gql`
    query TripAdherence($feedCode: String!, $tripId: String!, $startDate: String!, $endDate: String!) {
  tripAdherence(
    feedCode: $feedCode
    tripId: $tripId
    startDate: $startDate
    endDate: $endDate
  ) {
    serviceDate
    stopId
    stopName
    stopPathIndex
    scheduledSec
    observedSec
    adherenceSec
    vehicleId
    direction
    occupancyStatus
  }
}
    `;

/**
 * __useTripAdherenceQuery__
 *
 * To run a query within a Vue component, call `useTripAdherenceQuery` and pass it any options that fit your needs.
 * When your component renders, `useTripAdherenceQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useTripAdherenceQuery({
 *   feedCode: // value for 'feedCode'
 *   tripId: // value for 'tripId'
 *   startDate: // value for 'startDate'
 *   endDate: // value for 'endDate'
 * });
 */
export function useTripAdherenceQuery(variables: TripAdherenceQueryVariables | VueCompositionApi.Ref<TripAdherenceQueryVariables> | ReactiveFunction<TripAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<TripAdherenceQuery, TripAdherenceQueryVariables>(TripAdherenceDocument, variables, options);
}
export function useTripAdherenceLazyQuery(variables?: TripAdherenceQueryVariables | VueCompositionApi.Ref<TripAdherenceQueryVariables> | ReactiveFunction<TripAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripAdherenceQuery, TripAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<TripAdherenceQuery, TripAdherenceQueryVariables>(TripAdherenceDocument, variables, options);
}
export type TripAdherenceQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<TripAdherenceQuery, TripAdherenceQueryVariables>;
export const AllTripsAdherenceDocument = gql`
    query AllTripsAdherence($feedCode: String!, $routeId: String, $startDate: String!, $endDate: String!) {
  allTripsAdherence(
    feedCode: $feedCode
    routeId: $routeId
    startDate: $startDate
    endDate: $endDate
  ) {
    tripId
    routeId
    serviceDate
    sampleCount
    meanAdherenceSec
    onTimePct
  }
}
    `;

/**
 * __useAllTripsAdherenceQuery__
 *
 * To run a query within a Vue component, call `useAllTripsAdherenceQuery` and pass it any options that fit your needs.
 * When your component renders, `useAllTripsAdherenceQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAllTripsAdherenceQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 *   startDate: // value for 'startDate'
 *   endDate: // value for 'endDate'
 * });
 */
export function useAllTripsAdherenceQuery(variables: AllTripsAdherenceQueryVariables | VueCompositionApi.Ref<AllTripsAdherenceQueryVariables> | ReactiveFunction<AllTripsAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>(AllTripsAdherenceDocument, variables, options);
}
export function useAllTripsAdherenceLazyQuery(variables?: AllTripsAdherenceQueryVariables | VueCompositionApi.Ref<AllTripsAdherenceQueryVariables> | ReactiveFunction<AllTripsAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>(AllTripsAdherenceDocument, variables, options);
}
export type AllTripsAdherenceQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AllTripsAdherenceQuery, AllTripsAdherenceQueryVariables>;
export const StopAdherenceDocument = gql`
    query StopAdherence($feedCode: String!, $stopId: String!, $startDate: String!, $endDate: String!) {
  stopAdherence(
    feedCode: $feedCode
    stopId: $stopId
    startDate: $startDate
    endDate: $endDate
  ) {
    tripId
    routeId
    serviceDate
    stopPathIndex
    scheduledSec
    observedSec
    adherenceSec
  }
}
    `;

/**
 * __useStopAdherenceQuery__
 *
 * To run a query within a Vue component, call `useStopAdherenceQuery` and pass it any options that fit your needs.
 * When your component renders, `useStopAdherenceQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useStopAdherenceQuery({
 *   feedCode: // value for 'feedCode'
 *   stopId: // value for 'stopId'
 *   startDate: // value for 'startDate'
 *   endDate: // value for 'endDate'
 * });
 */
export function useStopAdherenceQuery(variables: StopAdherenceQueryVariables | VueCompositionApi.Ref<StopAdherenceQueryVariables> | ReactiveFunction<StopAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<StopAdherenceQuery, StopAdherenceQueryVariables>(StopAdherenceDocument, variables, options);
}
export function useStopAdherenceLazyQuery(variables?: StopAdherenceQueryVariables | VueCompositionApi.Ref<StopAdherenceQueryVariables> | ReactiveFunction<StopAdherenceQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopAdherenceQuery, StopAdherenceQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<StopAdherenceQuery, StopAdherenceQueryVariables>(StopAdherenceDocument, variables, options);
}
export type StopAdherenceQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<StopAdherenceQuery, StopAdherenceQueryVariables>;
export const AgenciesDocument = gql`
    query Agencies($feedCode: String!) {
  agencies(feedCode: $feedCode) {
    agencyId
    extent {
      minLat
      minLon
      maxLat
      maxLon
    }
  }
}
    `;

/**
 * __useAgenciesQuery__
 *
 * To run a query within a Vue component, call `useAgenciesQuery` and pass it any options that fit your needs.
 * When your component renders, `useAgenciesQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAgenciesQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useAgenciesQuery(variables: AgenciesQueryVariables | VueCompositionApi.Ref<AgenciesQueryVariables> | ReactiveFunction<AgenciesQueryVariables>, options: VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AgenciesQuery, AgenciesQueryVariables>(AgenciesDocument, variables, options);
}
export function useAgenciesLazyQuery(variables?: AgenciesQueryVariables | VueCompositionApi.Ref<AgenciesQueryVariables> | ReactiveFunction<AgenciesQueryVariables>, options: VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AgenciesQuery, AgenciesQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AgenciesQuery, AgenciesQueryVariables>(AgenciesDocument, variables, options);
}
export type AgenciesQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AgenciesQuery, AgenciesQueryVariables>;
export const AgenciesDetailDocument = gql`
    query AgenciesDetail($feedCode: String!) {
  agencies(feedCode: $feedCode) {
    agencyId
    agencyName
    agencyUrl
    agencyTimezone
    agencyPhone
    agencyEmail
    extent {
      minLat
      minLon
      maxLat
      maxLon
    }
  }
}
    `;

/**
 * __useAgenciesDetailQuery__
 *
 * To run a query within a Vue component, call `useAgenciesDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useAgenciesDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAgenciesDetailQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useAgenciesDetailQuery(variables: AgenciesDetailQueryVariables | VueCompositionApi.Ref<AgenciesDetailQueryVariables> | ReactiveFunction<AgenciesDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AgenciesDetailQuery, AgenciesDetailQueryVariables>(AgenciesDetailDocument, variables, options);
}
export function useAgenciesDetailLazyQuery(variables?: AgenciesDetailQueryVariables | VueCompositionApi.Ref<AgenciesDetailQueryVariables> | ReactiveFunction<AgenciesDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AgenciesDetailQuery, AgenciesDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AgenciesDetailQuery, AgenciesDetailQueryVariables>(AgenciesDetailDocument, variables, options);
}
export type AgenciesDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AgenciesDetailQuery, AgenciesDetailQueryVariables>;
export const AvlFeedsDocument = gql`
    query AvlFeeds {
  avlFeeds {
    code
    name
    gtfsFeedCode
    format
    predictionAlgorithms
    enabled
    pollIntervalSec
    lastPollAt
    lastPollStatus
    lastPollReportCount
  }
}
    `;

/**
 * __useAvlFeedsQuery__
 *
 * To run a query within a Vue component, call `useAvlFeedsQuery` and pass it any options that fit your needs.
 * When your component renders, `useAvlFeedsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAvlFeedsQuery();
 */
export function useAvlFeedsQuery(options: VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AvlFeedsQuery, AvlFeedsQueryVariables>(AvlFeedsDocument, {}, options);
}
export function useAvlFeedsLazyQuery(options: VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlFeedsQuery, AvlFeedsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AvlFeedsQuery, AvlFeedsQueryVariables>(AvlFeedsDocument, {}, options);
}
export type AvlFeedsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AvlFeedsQuery, AvlFeedsQueryVariables>;
export const AvlLogDocument = gql`
    query AvlLog($feedCode: String!, $vehicleId: String!, $limit: Int) {
  avlReports(feedCode: $feedCode, vehicleId: $vehicleId, limit: $limit) {
    ts
    position {
      lat
      lon
    }
    speedMps
    bearing
    matchStatus
    descTripId
    match {
      stopPathIndex
      distanceAlongTripM
      deviationM
      scheduleAdherenceSec
      score
      trip {
        tripId
        tripHeadsign
        route {
          routeShortName
          routeColor
        }
      }
    }
  }
}
    `;

/**
 * __useAvlLogQuery__
 *
 * To run a query within a Vue component, call `useAvlLogQuery` and pass it any options that fit your needs.
 * When your component renders, `useAvlLogQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAvlLogQuery({
 *   feedCode: // value for 'feedCode'
 *   vehicleId: // value for 'vehicleId'
 *   limit: // value for 'limit'
 * });
 */
export function useAvlLogQuery(variables: AvlLogQueryVariables | VueCompositionApi.Ref<AvlLogQueryVariables> | ReactiveFunction<AvlLogQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AvlLogQuery, AvlLogQueryVariables>(AvlLogDocument, variables, options);
}
export function useAvlLogLazyQuery(variables?: AvlLogQueryVariables | VueCompositionApi.Ref<AvlLogQueryVariables> | ReactiveFunction<AvlLogQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlLogQuery, AvlLogQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AvlLogQuery, AvlLogQueryVariables>(AvlLogDocument, variables, options);
}
export type AvlLogQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AvlLogQuery, AvlLogQueryVariables>;
export const AvlReportCatalogDocument = gql`
    query AvlReportCatalog($feedCode: String!) {
  avlReportCatalog(feedCode: $feedCode) {
    timeZone
    vehicleIds
  }
}
    `;

/**
 * __useAvlReportCatalogQuery__
 *
 * To run a query within a Vue component, call `useAvlReportCatalogQuery` and pass it any options that fit your needs.
 * When your component renders, `useAvlReportCatalogQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAvlReportCatalogQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useAvlReportCatalogQuery(variables: AvlReportCatalogQueryVariables | VueCompositionApi.Ref<AvlReportCatalogQueryVariables> | ReactiveFunction<AvlReportCatalogQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>(AvlReportCatalogDocument, variables, options);
}
export function useAvlReportCatalogLazyQuery(variables?: AvlReportCatalogQueryVariables | VueCompositionApi.Ref<AvlReportCatalogQueryVariables> | ReactiveFunction<AvlReportCatalogQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>(AvlReportCatalogDocument, variables, options);
}
export type AvlReportCatalogQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AvlReportCatalogQuery, AvlReportCatalogQueryVariables>;
export const AvlReportPageDocument = gql`
    query AvlReportPage($feedCode: String!, $vehicleId: String!, $date: String!, $startTime: String!, $endTime: String!, $page: Int, $pageSize: Int) {
  avlReportPage(
    feedCode: $feedCode
    vehicleId: $vehicleId
    date: $date
    startTime: $startTime
    endTime: $endTime
    page: $page
    pageSize: $pageSize
  ) {
    totalCount
    page
    pageSize
    items {
      vehicleId
      ts
      processedAt
      source
      position {
        lat
        lon
      }
      speedMps
      descTripId
      match {
        trip {
          tripId
        }
      }
    }
  }
}
    `;

/**
 * __useAvlReportPageQuery__
 *
 * To run a query within a Vue component, call `useAvlReportPageQuery` and pass it any options that fit your needs.
 * When your component renders, `useAvlReportPageQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAvlReportPageQuery({
 *   feedCode: // value for 'feedCode'
 *   vehicleId: // value for 'vehicleId'
 *   date: // value for 'date'
 *   startTime: // value for 'startTime'
 *   endTime: // value for 'endTime'
 *   page: // value for 'page'
 *   pageSize: // value for 'pageSize'
 * });
 */
export function useAvlReportPageQuery(variables: AvlReportPageQueryVariables | VueCompositionApi.Ref<AvlReportPageQueryVariables> | ReactiveFunction<AvlReportPageQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AvlReportPageQuery, AvlReportPageQueryVariables>(AvlReportPageDocument, variables, options);
}
export function useAvlReportPageLazyQuery(variables?: AvlReportPageQueryVariables | VueCompositionApi.Ref<AvlReportPageQueryVariables> | ReactiveFunction<AvlReportPageQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlReportPageQuery, AvlReportPageQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AvlReportPageQuery, AvlReportPageQueryVariables>(AvlReportPageDocument, variables, options);
}
export type AvlReportPageQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AvlReportPageQuery, AvlReportPageQueryVariables>;
export const AvlTrailDocument = gql`
    query AvlTrail($feedCode: String!, $vehicleId: String!, $limit: Int) {
  avlReports(feedCode: $feedCode, vehicleId: $vehicleId, limit: $limit) {
    vehicleId
    ts
    position {
      lat
      lon
    }
    speedMps
    bearing
    currentStatus
    occupancyStatus
    descTripId
  }
}
    `;

/**
 * __useAvlTrailQuery__
 *
 * To run a query within a Vue component, call `useAvlTrailQuery` and pass it any options that fit your needs.
 * When your component renders, `useAvlTrailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useAvlTrailQuery({
 *   feedCode: // value for 'feedCode'
 *   vehicleId: // value for 'vehicleId'
 *   limit: // value for 'limit'
 * });
 */
export function useAvlTrailQuery(variables: AvlTrailQueryVariables | VueCompositionApi.Ref<AvlTrailQueryVariables> | ReactiveFunction<AvlTrailQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<AvlTrailQuery, AvlTrailQueryVariables>(AvlTrailDocument, variables, options);
}
export function useAvlTrailLazyQuery(variables?: AvlTrailQueryVariables | VueCompositionApi.Ref<AvlTrailQueryVariables> | ReactiveFunction<AvlTrailQueryVariables>, options: VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<AvlTrailQuery, AvlTrailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<AvlTrailQuery, AvlTrailQueryVariables>(AvlTrailDocument, variables, options);
}
export type AvlTrailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<AvlTrailQuery, AvlTrailQueryVariables>;
export const BlockDetailDocument = gql`
    query BlockDetail($feedCode: String!, $blockId: String!, $serviceId: String!) {
  block(feedCode: $feedCode, blockId: $blockId, serviceId: $serviceId) {
    blockId
    serviceId
    startTimeSec
    endTimeSec
    tripCount
    routeIds
    blockTrips {
      listIndex
      layoverAfterSec
      deadheadAfter
      trip {
        tripId
        tripHeadsign
        routeId
        directionId
        startTimeSec
        endTimeSec
        route {
          routeShortName
          routeColor
          routeTextColor
        }
      }
    }
  }
}
    `;

/**
 * __useBlockDetailQuery__
 *
 * To run a query within a Vue component, call `useBlockDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useBlockDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useBlockDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   blockId: // value for 'blockId'
 *   serviceId: // value for 'serviceId'
 * });
 */
export function useBlockDetailQuery(variables: BlockDetailQueryVariables | VueCompositionApi.Ref<BlockDetailQueryVariables> | ReactiveFunction<BlockDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<BlockDetailQuery, BlockDetailQueryVariables>(BlockDetailDocument, variables, options);
}
export function useBlockDetailLazyQuery(variables?: BlockDetailQueryVariables | VueCompositionApi.Ref<BlockDetailQueryVariables> | ReactiveFunction<BlockDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<BlockDetailQuery, BlockDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<BlockDetailQuery, BlockDetailQueryVariables>(BlockDetailDocument, variables, options);
}
export type BlockDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<BlockDetailQuery, BlockDetailQueryVariables>;
export const BlocksListDocument = gql`
    query BlocksList($feedCode: String!) {
  blocks(feedCode: $feedCode) {
    blockId
    serviceId
    startTimeSec
    endTimeSec
    tripCount
    routeIds
    trips {
      routeId
    }
  }
}
    `;

/**
 * __useBlocksListQuery__
 *
 * To run a query within a Vue component, call `useBlocksListQuery` and pass it any options that fit your needs.
 * When your component renders, `useBlocksListQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useBlocksListQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useBlocksListQuery(variables: BlocksListQueryVariables | VueCompositionApi.Ref<BlocksListQueryVariables> | ReactiveFunction<BlocksListQueryVariables>, options: VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<BlocksListQuery, BlocksListQueryVariables>(BlocksListDocument, variables, options);
}
export function useBlocksListLazyQuery(variables?: BlocksListQueryVariables | VueCompositionApi.Ref<BlocksListQueryVariables> | ReactiveFunction<BlocksListQueryVariables>, options: VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<BlocksListQuery, BlocksListQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<BlocksListQuery, BlocksListQueryVariables>(BlocksListDocument, variables, options);
}
export type BlocksListQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<BlocksListQuery, BlocksListQueryVariables>;
export const CompareRevisionsDocument = gql`
    query CompareRevisions($fromRevisionId: ID!, $toRevisionId: ID!, $routeId: String, $directionId: Int, $serviceId: String, $offset: Int, $limit: Int) {
  compareRevisions(
    fromRevisionId: $fromRevisionId
    toRevisionId: $toRevisionId
    routeId: $routeId
    directionId: $directionId
    serviceId: $serviceId
    offset: $offset
    limit: $limit
  ) {
    fromRevisionId
    toRevisionId
    fromDerivationStale
    toDerivationStale
    tripChangeCount
    tripChanges {
      tripId
      kind
      fieldChanges
      runTimeDeltaSec
      stopChanges {
        stopSequence
        kind
        stopId
        arrivalDeltaSec
        departureDeltaSec
      }
    }
    calendarChanges {
      serviceId
      kind
      fieldChanges
      exceptionChanges {
        date
        kind
        fromType
        toType
      }
    }
    headwaySummaries {
      routeId
      directionId
      serviceId
      fromTripCount
      toTripCount
      fromMeanGapSec
      toMeanGapSec
      fromMaxGapSec
      toMaxGapSec
    }
  }
}
    `;

/**
 * __useCompareRevisionsQuery__
 *
 * To run a query within a Vue component, call `useCompareRevisionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useCompareRevisionsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useCompareRevisionsQuery({
 *   fromRevisionId: // value for 'fromRevisionId'
 *   toRevisionId: // value for 'toRevisionId'
 *   routeId: // value for 'routeId'
 *   directionId: // value for 'directionId'
 *   serviceId: // value for 'serviceId'
 *   offset: // value for 'offset'
 *   limit: // value for 'limit'
 * });
 */
export function useCompareRevisionsQuery(variables: CompareRevisionsQueryVariables | VueCompositionApi.Ref<CompareRevisionsQueryVariables> | ReactiveFunction<CompareRevisionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<CompareRevisionsQuery, CompareRevisionsQueryVariables>(CompareRevisionsDocument, variables, options);
}
export function useCompareRevisionsLazyQuery(variables?: CompareRevisionsQueryVariables | VueCompositionApi.Ref<CompareRevisionsQueryVariables> | ReactiveFunction<CompareRevisionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<CompareRevisionsQuery, CompareRevisionsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<CompareRevisionsQuery, CompareRevisionsQueryVariables>(CompareRevisionsDocument, variables, options);
}
export type CompareRevisionsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<CompareRevisionsQuery, CompareRevisionsQueryVariables>;
export const UpdateStopTimeDocument = gql`
    mutation UpdateStopTime($input: UpdateStopTimeInput!) {
  updateStopTime(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useUpdateStopTimeMutation__
 *
 * To run a mutation, you first call `useUpdateStopTimeMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useUpdateStopTimeMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useUpdateStopTimeMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useUpdateStopTimeMutation(options: VueApolloComposable.UseMutationOptions<UpdateStopTimeMutation, UpdateStopTimeMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>(UpdateStopTimeDocument, options);
}
export type UpdateStopTimeMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>;
export const ShiftTripDocument = gql`
    mutation ShiftTrip($input: ShiftTripInput!) {
  shiftTrip(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useShiftTripMutation__
 *
 * To run a mutation, you first call `useShiftTripMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useShiftTripMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useShiftTripMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useShiftTripMutation(options: VueApolloComposable.UseMutationOptions<ShiftTripMutation, ShiftTripMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ShiftTripMutation, ShiftTripMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ShiftTripMutation, ShiftTripMutationVariables>(ShiftTripDocument, options);
}
export type ShiftTripMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ShiftTripMutation, ShiftTripMutationVariables>;
export const SetStopDwellDocument = gql`
    mutation SetStopDwell($input: SetStopDwellInput!) {
  setStopDwell(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useSetStopDwellMutation__
 *
 * To run a mutation, you first call `useSetStopDwellMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useSetStopDwellMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useSetStopDwellMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useSetStopDwellMutation(options: VueApolloComposable.UseMutationOptions<SetStopDwellMutation, SetStopDwellMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<SetStopDwellMutation, SetStopDwellMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<SetStopDwellMutation, SetStopDwellMutationVariables>(SetStopDwellDocument, options);
}
export type SetStopDwellMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<SetStopDwellMutation, SetStopDwellMutationVariables>;
export const AddTripDocument = gql`
    mutation AddTrip($input: AddTripInput!) {
  addTrip(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useAddTripMutation__
 *
 * To run a mutation, you first call `useAddTripMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useAddTripMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useAddTripMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useAddTripMutation(options: VueApolloComposable.UseMutationOptions<AddTripMutation, AddTripMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<AddTripMutation, AddTripMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<AddTripMutation, AddTripMutationVariables>(AddTripDocument, options);
}
export type AddTripMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<AddTripMutation, AddTripMutationVariables>;
export const DuplicateTripDocument = gql`
    mutation DuplicateTrip($input: DuplicateTripInput!) {
  duplicateTrip(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useDuplicateTripMutation__
 *
 * To run a mutation, you first call `useDuplicateTripMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useDuplicateTripMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useDuplicateTripMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useDuplicateTripMutation(options: VueApolloComposable.UseMutationOptions<DuplicateTripMutation, DuplicateTripMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<DuplicateTripMutation, DuplicateTripMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<DuplicateTripMutation, DuplicateTripMutationVariables>(DuplicateTripDocument, options);
}
export type DuplicateTripMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<DuplicateTripMutation, DuplicateTripMutationVariables>;
export const DeleteTripDocument = gql`
    mutation DeleteTrip($input: DeleteTripInput!) {
  deleteTrip(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useDeleteTripMutation__
 *
 * To run a mutation, you first call `useDeleteTripMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useDeleteTripMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useDeleteTripMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useDeleteTripMutation(options: VueApolloComposable.UseMutationOptions<DeleteTripMutation, DeleteTripMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<DeleteTripMutation, DeleteTripMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<DeleteTripMutation, DeleteTripMutationVariables>(DeleteTripDocument, options);
}
export type DeleteTripMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<DeleteTripMutation, DeleteTripMutationVariables>;
export const BulkShiftTripsDocument = gql`
    mutation BulkShiftTrips($input: BulkShiftTripsInput!) {
  bulkShiftTrips(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useBulkShiftTripsMutation__
 *
 * To run a mutation, you first call `useBulkShiftTripsMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useBulkShiftTripsMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useBulkShiftTripsMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useBulkShiftTripsMutation(options: VueApolloComposable.UseMutationOptions<BulkShiftTripsMutation, BulkShiftTripsMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>(BulkShiftTripsDocument, options);
}
export type BulkShiftTripsMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>;
export const InsertTripStopDocument = gql`
    mutation InsertTripStop($input: InsertTripStopInput!) {
  insertTripStop(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useInsertTripStopMutation__
 *
 * To run a mutation, you first call `useInsertTripStopMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useInsertTripStopMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useInsertTripStopMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useInsertTripStopMutation(options: VueApolloComposable.UseMutationOptions<InsertTripStopMutation, InsertTripStopMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<InsertTripStopMutation, InsertTripStopMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<InsertTripStopMutation, InsertTripStopMutationVariables>(InsertTripStopDocument, options);
}
export type InsertTripStopMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<InsertTripStopMutation, InsertTripStopMutationVariables>;
export const RemoveTripStopDocument = gql`
    mutation RemoveTripStop($input: RemoveTripStopInput!) {
  removeTripStop(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useRemoveTripStopMutation__
 *
 * To run a mutation, you first call `useRemoveTripStopMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useRemoveTripStopMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useRemoveTripStopMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useRemoveTripStopMutation(options: VueApolloComposable.UseMutationOptions<RemoveTripStopMutation, RemoveTripStopMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<RemoveTripStopMutation, RemoveTripStopMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<RemoveTripStopMutation, RemoveTripStopMutationVariables>(RemoveTripStopDocument, options);
}
export type RemoveTripStopMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<RemoveTripStopMutation, RemoveTripStopMutationVariables>;
export const ReorderTripStopsDocument = gql`
    mutation ReorderTripStops($input: ReorderTripStopsInput!) {
  reorderTripStops(input: $input) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useReorderTripStopsMutation__
 *
 * To run a mutation, you first call `useReorderTripStopsMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useReorderTripStopsMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useReorderTripStopsMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useReorderTripStopsMutation(options: VueApolloComposable.UseMutationOptions<ReorderTripStopsMutation, ReorderTripStopsMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>(ReorderTripStopsDocument, options);
}
export type ReorderTripStopsMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ReorderTripStopsMutation, ReorderTripStopsMutationVariables>;
export const UndoDraftEditDocument = gql`
    mutation UndoDraftEdit($id: ID!, $editor: String!, $expectedVersion: Long!) {
  undoDraftEdit(id: $id, editor: $editor, expectedVersion: $expectedVersion) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useUndoDraftEditMutation__
 *
 * To run a mutation, you first call `useUndoDraftEditMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useUndoDraftEditMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useUndoDraftEditMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *     expectedVersion: // value for 'expectedVersion'
 *   },
 * });
 */
export function useUndoDraftEditMutation(options: VueApolloComposable.UseMutationOptions<UndoDraftEditMutation, UndoDraftEditMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<UndoDraftEditMutation, UndoDraftEditMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<UndoDraftEditMutation, UndoDraftEditMutationVariables>(UndoDraftEditDocument, options);
}
export type UndoDraftEditMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<UndoDraftEditMutation, UndoDraftEditMutationVariables>;
export const RedoDraftEditDocument = gql`
    mutation RedoDraftEdit($id: ID!, $editor: String!, $expectedVersion: Long!) {
  redoDraftEdit(id: $id, editor: $editor, expectedVersion: $expectedVersion) {
    ...DraftEditResultF
  }
}
    ${DraftEditResultFFragmentDoc}`;

/**
 * __useRedoDraftEditMutation__
 *
 * To run a mutation, you first call `useRedoDraftEditMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useRedoDraftEditMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useRedoDraftEditMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *     expectedVersion: // value for 'expectedVersion'
 *   },
 * });
 */
export function useRedoDraftEditMutation(options: VueApolloComposable.UseMutationOptions<RedoDraftEditMutation, RedoDraftEditMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<RedoDraftEditMutation, RedoDraftEditMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<RedoDraftEditMutation, RedoDraftEditMutationVariables>(RedoDraftEditDocument, options);
}
export type RedoDraftEditMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<RedoDraftEditMutation, RedoDraftEditMutationVariables>;
export const DraftGridDocument = gql`
    query DraftGrid($draftId: ID!, $routeId: String!, $directionId: Int, $serviceId: String) {
  draftGrid(
    draftId: $draftId
    routeId: $routeId
    directionId: $directionId
    serviceId: $serviceId
  ) {
    stops {
      stopSequence
      stopId
      stopName
      timepoint
    }
    trips {
      tripId
      firstDepartureSec
      headsign
      blockId
      directionId
      serviceId
      cells {
        stopSequence
        arrivalSec
        departureSec
      }
    }
  }
}
    `;

/**
 * __useDraftGridQuery__
 *
 * To run a query within a Vue component, call `useDraftGridQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftGridQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftGridQuery({
 *   draftId: // value for 'draftId'
 *   routeId: // value for 'routeId'
 *   directionId: // value for 'directionId'
 *   serviceId: // value for 'serviceId'
 * });
 */
export function useDraftGridQuery(variables: DraftGridQueryVariables | VueCompositionApi.Ref<DraftGridQueryVariables> | ReactiveFunction<DraftGridQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftGridQuery, DraftGridQueryVariables>(DraftGridDocument, variables, options);
}
export function useDraftGridLazyQuery(variables?: DraftGridQueryVariables | VueCompositionApi.Ref<DraftGridQueryVariables> | ReactiveFunction<DraftGridQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftGridQuery, DraftGridQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftGridQuery, DraftGridQueryVariables>(DraftGridDocument, variables, options);
}
export type DraftGridQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftGridQuery, DraftGridQueryVariables>;
export const ForkDraftDocument = gql`
    mutation ForkDraft($input: ForkDraftInput!) {
  forkDraft(input: $input) {
    id
    feedCode
    label
    version
  }
}
    `;

/**
 * __useForkDraftMutation__
 *
 * To run a mutation, you first call `useForkDraftMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useForkDraftMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useForkDraftMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useForkDraftMutation(options: VueApolloComposable.UseMutationOptions<ForkDraftMutation, ForkDraftMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ForkDraftMutation, ForkDraftMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ForkDraftMutation, ForkDraftMutationVariables>(ForkDraftDocument, options);
}
export type ForkDraftMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ForkDraftMutation, ForkDraftMutationVariables>;
export const DiscardDraftDocument = gql`
    mutation DiscardDraft($id: ID!, $editor: String!) {
  discardDraft(id: $id, editor: $editor)
}
    `;

/**
 * __useDiscardDraftMutation__
 *
 * To run a mutation, you first call `useDiscardDraftMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useDiscardDraftMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useDiscardDraftMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *   },
 * });
 */
export function useDiscardDraftMutation(options: VueApolloComposable.UseMutationOptions<DiscardDraftMutation, DiscardDraftMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<DiscardDraftMutation, DiscardDraftMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<DiscardDraftMutation, DiscardDraftMutationVariables>(DiscardDraftDocument, options);
}
export type DiscardDraftMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<DiscardDraftMutation, DiscardDraftMutationVariables>;
export const RevertDraftToForkDocument = gql`
    mutation RevertDraftToFork($id: ID!, $editor: String!) {
  revertDraftToFork(id: $id, editor: $editor) {
    id
    version
    derivationStale
  }
}
    `;

/**
 * __useRevertDraftToForkMutation__
 *
 * To run a mutation, you first call `useRevertDraftToForkMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useRevertDraftToForkMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useRevertDraftToForkMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *   },
 * });
 */
export function useRevertDraftToForkMutation(options: VueApolloComposable.UseMutationOptions<RevertDraftToForkMutation, RevertDraftToForkMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>(RevertDraftToForkDocument, options);
}
export type RevertDraftToForkMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<RevertDraftToForkMutation, RevertDraftToForkMutationVariables>;
export const ActivateDraftDocument = gql`
    mutation ActivateDraft($id: ID!, $editor: String!, $force: Boolean) {
  activateDraft(id: $id, editor: $editor, force: $force) {
    id
    status
  }
}
    `;

/**
 * __useActivateDraftMutation__
 *
 * To run a mutation, you first call `useActivateDraftMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useActivateDraftMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useActivateDraftMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *     force: // value for 'force'
 *   },
 * });
 */
export function useActivateDraftMutation(options: VueApolloComposable.UseMutationOptions<ActivateDraftMutation, ActivateDraftMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ActivateDraftMutation, ActivateDraftMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ActivateDraftMutation, ActivateDraftMutationVariables>(ActivateDraftDocument, options);
}
export type ActivateDraftMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ActivateDraftMutation, ActivateDraftMutationVariables>;
export const RebuildDraftDocument = gql`
    mutation RebuildDraft($id: ID!) {
  rebuildDraft(id: $id) {
    id
    state
    phase
    error
  }
}
    `;

/**
 * __useRebuildDraftMutation__
 *
 * To run a mutation, you first call `useRebuildDraftMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useRebuildDraftMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useRebuildDraftMutation({
 *   variables: {
 *     id: // value for 'id'
 *   },
 * });
 */
export function useRebuildDraftMutation(options: VueApolloComposable.UseMutationOptions<RebuildDraftMutation, RebuildDraftMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<RebuildDraftMutation, RebuildDraftMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<RebuildDraftMutation, RebuildDraftMutationVariables>(RebuildDraftDocument, options);
}
export type RebuildDraftMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<RebuildDraftMutation, RebuildDraftMutationVariables>;
export const ClaimDraftEditorDocument = gql`
    mutation ClaimDraftEditor($id: ID!, $editor: String!, $takeOver: Boolean) {
  claimDraftEditor(id: $id, editor: $editor, takeOver: $takeOver) {
    editor
    expiresAt
  }
}
    `;

/**
 * __useClaimDraftEditorMutation__
 *
 * To run a mutation, you first call `useClaimDraftEditorMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useClaimDraftEditorMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useClaimDraftEditorMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *     takeOver: // value for 'takeOver'
 *   },
 * });
 */
export function useClaimDraftEditorMutation(options: VueApolloComposable.UseMutationOptions<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>(ClaimDraftEditorDocument, options);
}
export type ClaimDraftEditorMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>;
export const RenewDraftEditorDocument = gql`
    mutation RenewDraftEditor($id: ID!, $editor: String!) {
  renewDraftEditor(id: $id, editor: $editor) {
    editor
    expiresAt
  }
}
    `;

/**
 * __useRenewDraftEditorMutation__
 *
 * To run a mutation, you first call `useRenewDraftEditorMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useRenewDraftEditorMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useRenewDraftEditorMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *   },
 * });
 */
export function useRenewDraftEditorMutation(options: VueApolloComposable.UseMutationOptions<RenewDraftEditorMutation, RenewDraftEditorMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>(RenewDraftEditorDocument, options);
}
export type RenewDraftEditorMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>;
export const ReleaseDraftEditorDocument = gql`
    mutation ReleaseDraftEditor($id: ID!, $editor: String!) {
  releaseDraftEditor(id: $id, editor: $editor)
}
    `;

/**
 * __useReleaseDraftEditorMutation__
 *
 * To run a mutation, you first call `useReleaseDraftEditorMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useReleaseDraftEditorMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useReleaseDraftEditorMutation({
 *   variables: {
 *     id: // value for 'id'
 *     editor: // value for 'editor'
 *   },
 * });
 */
export function useReleaseDraftEditorMutation(options: VueApolloComposable.UseMutationOptions<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>(ReleaseDraftEditorDocument, options);
}
export type ReleaseDraftEditorMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>;
export const DraftPatternsDocument = gql`
    query DraftPatterns($feedCode: String!, $routeId: String!, $revisionId: ID!) {
  tripPatterns(feedCode: $feedCode, routeId: $routeId, revisionId: $revisionId) {
    patternKey
    directionId
    headsign
    stopCount
    stopPaths {
      stopPathIndex
      stopId
      typicalTravelTimeSec
      typicalDwellTimeSec
    }
  }
}
    `;

/**
 * __useDraftPatternsQuery__
 *
 * To run a query within a Vue component, call `useDraftPatternsQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftPatternsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftPatternsQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 *   revisionId: // value for 'revisionId'
 * });
 */
export function useDraftPatternsQuery(variables: DraftPatternsQueryVariables | VueCompositionApi.Ref<DraftPatternsQueryVariables> | ReactiveFunction<DraftPatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftPatternsQuery, DraftPatternsQueryVariables>(DraftPatternsDocument, variables, options);
}
export function useDraftPatternsLazyQuery(variables?: DraftPatternsQueryVariables | VueCompositionApi.Ref<DraftPatternsQueryVariables> | ReactiveFunction<DraftPatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftPatternsQuery, DraftPatternsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftPatternsQuery, DraftPatternsQueryVariables>(DraftPatternsDocument, variables, options);
}
export type DraftPatternsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftPatternsQuery, DraftPatternsQueryVariables>;
export const DraftTripDetailDocument = gql`
    query DraftTripDetail($feedCode: String!, $tripId: String!, $revisionId: ID!) {
  trip(feedCode: $feedCode, tripId: $tripId, revisionId: $revisionId) {
    tripId
    tripHeadsign
    routeId
    directionId
    serviceId
    blockId
    shapeId
    startTimeSec
    endTimeSec
    frequencyBased
    noSchedule
    pattern {
      patternKey
      stopCount
      lengthM
    }
    block {
      blockId
      serviceId
    }
  }
}
    `;

/**
 * __useDraftTripDetailQuery__
 *
 * To run a query within a Vue component, call `useDraftTripDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftTripDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftTripDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   tripId: // value for 'tripId'
 *   revisionId: // value for 'revisionId'
 * });
 */
export function useDraftTripDetailQuery(variables: DraftTripDetailQueryVariables | VueCompositionApi.Ref<DraftTripDetailQueryVariables> | ReactiveFunction<DraftTripDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftTripDetailQuery, DraftTripDetailQueryVariables>(DraftTripDetailDocument, variables, options);
}
export function useDraftTripDetailLazyQuery(variables?: DraftTripDetailQueryVariables | VueCompositionApi.Ref<DraftTripDetailQueryVariables> | ReactiveFunction<DraftTripDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftTripDetailQuery, DraftTripDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftTripDetailQuery, DraftTripDetailQueryVariables>(DraftTripDetailDocument, variables, options);
}
export type DraftTripDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftTripDetailQuery, DraftTripDetailQueryVariables>;
export const DraftsDocument = gql`
    query Drafts($feedCode: String) {
  drafts(feedCode: $feedCode) {
    id
    feedCode
    label
    baseRevisionId
    status
    kind
    version
    derivationStale
    lastValidation
    lock {
      editor
      expiresAt
    }
    createdBy
    createdAt
    rowCounts
  }
}
    `;

/**
 * __useDraftsQuery__
 *
 * To run a query within a Vue component, call `useDraftsQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftsQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useDraftsQuery(variables: DraftsQueryVariables | VueCompositionApi.Ref<DraftsQueryVariables> | ReactiveFunction<DraftsQueryVariables> = {}, options: VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftsQuery, DraftsQueryVariables>(DraftsDocument, variables, options);
}
export function useDraftsLazyQuery(variables: DraftsQueryVariables | VueCompositionApi.Ref<DraftsQueryVariables> | ReactiveFunction<DraftsQueryVariables> = {}, options: VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftsQuery, DraftsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftsQuery, DraftsQueryVariables>(DraftsDocument, variables, options);
}
export type DraftsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftsQuery, DraftsQueryVariables>;
export const DraftDetailDocument = gql`
    query DraftDetail($id: ID!) {
  draft(id: $id) {
    id
    feedCode
    label
    baseRevisionId
    status
    kind
    version
    derivationStale
    lastValidation
    lock {
      editor
      expiresAt
    }
    createdBy
    createdAt
    rowCounts
  }
}
    `;

/**
 * __useDraftDetailQuery__
 *
 * To run a query within a Vue component, call `useDraftDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftDetailQuery({
 *   id: // value for 'id'
 * });
 */
export function useDraftDetailQuery(variables: DraftDetailQueryVariables | VueCompositionApi.Ref<DraftDetailQueryVariables> | ReactiveFunction<DraftDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftDetailQuery, DraftDetailQueryVariables>(DraftDetailDocument, variables, options);
}
export function useDraftDetailLazyQuery(variables?: DraftDetailQueryVariables | VueCompositionApi.Ref<DraftDetailQueryVariables> | ReactiveFunction<DraftDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftDetailQuery, DraftDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftDetailQuery, DraftDetailQueryVariables>(DraftDetailDocument, variables, options);
}
export type DraftDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftDetailQuery, DraftDetailQueryVariables>;
export const DraftEditsDocument = gql`
    query DraftEdits($id: ID!, $limit: Int) {
  draftEdits(id: $id, limit: $limit) {
    seq
    op
    summary
    appliedAt
    undone
  }
}
    `;

/**
 * __useDraftEditsQuery__
 *
 * To run a query within a Vue component, call `useDraftEditsQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftEditsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftEditsQuery({
 *   id: // value for 'id'
 *   limit: // value for 'limit'
 * });
 */
export function useDraftEditsQuery(variables: DraftEditsQueryVariables | VueCompositionApi.Ref<DraftEditsQueryVariables> | ReactiveFunction<DraftEditsQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftEditsQuery, DraftEditsQueryVariables>(DraftEditsDocument, variables, options);
}
export function useDraftEditsLazyQuery(variables?: DraftEditsQueryVariables | VueCompositionApi.Ref<DraftEditsQueryVariables> | ReactiveFunction<DraftEditsQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftEditsQuery, DraftEditsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftEditsQuery, DraftEditsQueryVariables>(DraftEditsDocument, variables, options);
}
export type DraftEditsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftEditsQuery, DraftEditsQueryVariables>;
export const DraftJobDocument = gql`
    query DraftJob($jobId: ID!) {
  draftJob(jobId: $jobId) {
    id
    state
    phase
    error
  }
}
    `;

/**
 * __useDraftJobQuery__
 *
 * To run a query within a Vue component, call `useDraftJobQuery` and pass it any options that fit your needs.
 * When your component renders, `useDraftJobQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useDraftJobQuery({
 *   jobId: // value for 'jobId'
 * });
 */
export function useDraftJobQuery(variables: DraftJobQueryVariables | VueCompositionApi.Ref<DraftJobQueryVariables> | ReactiveFunction<DraftJobQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<DraftJobQuery, DraftJobQueryVariables>(DraftJobDocument, variables, options);
}
export function useDraftJobLazyQuery(variables?: DraftJobQueryVariables | VueCompositionApi.Ref<DraftJobQueryVariables> | ReactiveFunction<DraftJobQueryVariables>, options: VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<DraftJobQuery, DraftJobQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<DraftJobQuery, DraftJobQueryVariables>(DraftJobDocument, variables, options);
}
export type DraftJobQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<DraftJobQuery, DraftJobQueryVariables>;
export const ExploreCalendarDocument = gql`
    query ExploreCalendar($feedCode: String!) {
  calendars(feedCode: $feedCode) {
    serviceId
    monday
    tuesday
    wednesday
    thursday
    friday
    saturday
    sunday
    startDate
    endDate
  }
  calendarDates(feedCode: $feedCode) {
    serviceId
    date
    exceptionType
  }
}
    `;

/**
 * __useExploreCalendarQuery__
 *
 * To run a query within a Vue component, call `useExploreCalendarQuery` and pass it any options that fit your needs.
 * When your component renders, `useExploreCalendarQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useExploreCalendarQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useExploreCalendarQuery(variables: ExploreCalendarQueryVariables | VueCompositionApi.Ref<ExploreCalendarQueryVariables> | ReactiveFunction<ExploreCalendarQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<ExploreCalendarQuery, ExploreCalendarQueryVariables>(ExploreCalendarDocument, variables, options);
}
export function useExploreCalendarLazyQuery(variables?: ExploreCalendarQueryVariables | VueCompositionApi.Ref<ExploreCalendarQueryVariables> | ReactiveFunction<ExploreCalendarQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreCalendarQuery, ExploreCalendarQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<ExploreCalendarQuery, ExploreCalendarQueryVariables>(ExploreCalendarDocument, variables, options);
}
export type ExploreCalendarQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<ExploreCalendarQuery, ExploreCalendarQueryVariables>;
export const ExplorePatternsDocument = gql`
    query ExplorePatterns($feedCode: String!) {
  tripPatterns(feedCode: $feedCode) {
    patternKey
    routeId
    directionId
    headsign
    tripCount
    route {
      routeShortName
      routeLongName
      routeColor
      routeTextColor
      routeType
      agencyId
    }
    stopPaths {
      stopPathIndex
      stopId
      typicalTravelTimeSec
      typicalDwellTimeSec
    }
  }
}
    `;

/**
 * __useExplorePatternsQuery__
 *
 * To run a query within a Vue component, call `useExplorePatternsQuery` and pass it any options that fit your needs.
 * When your component renders, `useExplorePatternsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useExplorePatternsQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useExplorePatternsQuery(variables: ExplorePatternsQueryVariables | VueCompositionApi.Ref<ExplorePatternsQueryVariables> | ReactiveFunction<ExplorePatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<ExplorePatternsQuery, ExplorePatternsQueryVariables>(ExplorePatternsDocument, variables, options);
}
export function useExplorePatternsLazyQuery(variables?: ExplorePatternsQueryVariables | VueCompositionApi.Ref<ExplorePatternsQueryVariables> | ReactiveFunction<ExplorePatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExplorePatternsQuery, ExplorePatternsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<ExplorePatternsQuery, ExplorePatternsQueryVariables>(ExplorePatternsDocument, variables, options);
}
export type ExplorePatternsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<ExplorePatternsQuery, ExplorePatternsQueryVariables>;
export const ExploreRoutesDocument = gql`
    query ExploreRoutes($feedCode: String!) {
  routes(feedCode: $feedCode) {
    routeId
    routeShortName
    routeLongName
    routeType
    routeColor
    routeTextColor
    agencyId
  }
  agencies(feedCode: $feedCode) {
    agencyId
    agencyName
  }
}
    `;

/**
 * __useExploreRoutesQuery__
 *
 * To run a query within a Vue component, call `useExploreRoutesQuery` and pass it any options that fit your needs.
 * When your component renders, `useExploreRoutesQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useExploreRoutesQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useExploreRoutesQuery(variables: ExploreRoutesQueryVariables | VueCompositionApi.Ref<ExploreRoutesQueryVariables> | ReactiveFunction<ExploreRoutesQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<ExploreRoutesQuery, ExploreRoutesQueryVariables>(ExploreRoutesDocument, variables, options);
}
export function useExploreRoutesLazyQuery(variables?: ExploreRoutesQueryVariables | VueCompositionApi.Ref<ExploreRoutesQueryVariables> | ReactiveFunction<ExploreRoutesQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreRoutesQuery, ExploreRoutesQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<ExploreRoutesQuery, ExploreRoutesQueryVariables>(ExploreRoutesDocument, variables, options);
}
export type ExploreRoutesQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<ExploreRoutesQuery, ExploreRoutesQueryVariables>;
export const ExploreStopsDocument = gql`
    query ExploreStops($feedCode: String!) {
  stops(feedCode: $feedCode) {
    stopId
    stopName
    stopCode
    stopDesc
    stopLat
    stopLon
    locationType
    parentStation
  }
}
    `;

/**
 * __useExploreStopsQuery__
 *
 * To run a query within a Vue component, call `useExploreStopsQuery` and pass it any options that fit your needs.
 * When your component renders, `useExploreStopsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useExploreStopsQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useExploreStopsQuery(variables: ExploreStopsQueryVariables | VueCompositionApi.Ref<ExploreStopsQueryVariables> | ReactiveFunction<ExploreStopsQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<ExploreStopsQuery, ExploreStopsQueryVariables>(ExploreStopsDocument, variables, options);
}
export function useExploreStopsLazyQuery(variables?: ExploreStopsQueryVariables | VueCompositionApi.Ref<ExploreStopsQueryVariables> | ReactiveFunction<ExploreStopsQueryVariables>, options: VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<ExploreStopsQuery, ExploreStopsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<ExploreStopsQuery, ExploreStopsQueryVariables>(ExploreStopsDocument, variables, options);
}
export type ExploreStopsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<ExploreStopsQuery, ExploreStopsQueryVariables>;
export const FeedDetailDocument = gql`
    query FeedDetail($code: String!) {
  feed(code: $code) {
    code
    name
    description
    url
    pollingCron
    enabled
    source
    activeRevision {
      id
      status
      createdAt
      activatedAt
      feedStartDate
      feedEndDate
      byteSize
      contentSha256
      filesPresent
      rowCounts
      validationSummary {
        errorCount
        warningCount
        notices {
          rule
          severity
          count
          sample
        }
      }
    }
    revisions {
      id
      status
      createdAt
      activatedAt
      supersededAt
      feedStartDate
      feedEndDate
      byteSize
      contentSha256
      errorMessage
      validationSummary {
        errorCount
        warningCount
        notices {
          rule
          severity
          count
          sample
        }
      }
    }
  }
  feedInfo(feedCode: $code) {
    feedPublisherName
    feedPublisherUrl
    feedLang
    feedStartDate
    feedEndDate
    feedVersion
    feedContactEmail
    feedContactUrl
  }
}
    `;

/**
 * __useFeedDetailQuery__
 *
 * To run a query within a Vue component, call `useFeedDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useFeedDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useFeedDetailQuery({
 *   code: // value for 'code'
 * });
 */
export function useFeedDetailQuery(variables: FeedDetailQueryVariables | VueCompositionApi.Ref<FeedDetailQueryVariables> | ReactiveFunction<FeedDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<FeedDetailQuery, FeedDetailQueryVariables>(FeedDetailDocument, variables, options);
}
export function useFeedDetailLazyQuery(variables?: FeedDetailQueryVariables | VueCompositionApi.Ref<FeedDetailQueryVariables> | ReactiveFunction<FeedDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedDetailQuery, FeedDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<FeedDetailQuery, FeedDetailQueryVariables>(FeedDetailDocument, variables, options);
}
export type FeedDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<FeedDetailQuery, FeedDetailQueryVariables>;
export const FeedGeometryDocument = gql`
    query FeedGeometry($feedCode: String!) {
  tripPatterns(feedCode: $feedCode) {
    patternKey
    routeId
    headsign
    tripCount
    route {
      routeId
      routeShortName
      routeLongName
      routeColor
      routeType
    }
    stopPaths {
      stopPathIndex
      pathGeometry
    }
  }
  stops(feedCode: $feedCode) {
    stopId
    stopName
    stopLat
    stopLon
    locationType
  }
}
    `;

/**
 * __useFeedGeometryQuery__
 *
 * To run a query within a Vue component, call `useFeedGeometryQuery` and pass it any options that fit your needs.
 * When your component renders, `useFeedGeometryQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useFeedGeometryQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useFeedGeometryQuery(variables: FeedGeometryQueryVariables | VueCompositionApi.Ref<FeedGeometryQueryVariables> | ReactiveFunction<FeedGeometryQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<FeedGeometryQuery, FeedGeometryQueryVariables>(FeedGeometryDocument, variables, options);
}
export function useFeedGeometryLazyQuery(variables?: FeedGeometryQueryVariables | VueCompositionApi.Ref<FeedGeometryQueryVariables> | ReactiveFunction<FeedGeometryQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedGeometryQuery, FeedGeometryQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<FeedGeometryQuery, FeedGeometryQueryVariables>(FeedGeometryDocument, variables, options);
}
export type FeedGeometryQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<FeedGeometryQuery, FeedGeometryQueryVariables>;
export const IngestFeedDocument = gql`
    mutation IngestFeed($feedCode: String!) {
  ingestFeed(feedCode: $feedCode) {
    id
    status
  }
}
    `;

/**
 * __useIngestFeedMutation__
 *
 * To run a mutation, you first call `useIngestFeedMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useIngestFeedMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useIngestFeedMutation({
 *   variables: {
 *     feedCode: // value for 'feedCode'
 *   },
 * });
 */
export function useIngestFeedMutation(options: VueApolloComposable.UseMutationOptions<IngestFeedMutation, IngestFeedMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<IngestFeedMutation, IngestFeedMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<IngestFeedMutation, IngestFeedMutationVariables>(IngestFeedDocument, options);
}
export type IngestFeedMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<IngestFeedMutation, IngestFeedMutationVariables>;
export const ActivateRevisionDocument = gql`
    mutation ActivateRevision($revisionId: ID!) {
  activateRevision(revisionId: $revisionId) {
    id
    status
  }
}
    `;

/**
 * __useActivateRevisionMutation__
 *
 * To run a mutation, you first call `useActivateRevisionMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useActivateRevisionMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useActivateRevisionMutation({
 *   variables: {
 *     revisionId: // value for 'revisionId'
 *   },
 * });
 */
export function useActivateRevisionMutation(options: VueApolloComposable.UseMutationOptions<ActivateRevisionMutation, ActivateRevisionMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ActivateRevisionMutation, ActivateRevisionMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ActivateRevisionMutation, ActivateRevisionMutationVariables>(ActivateRevisionDocument, options);
}
export type ActivateRevisionMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ActivateRevisionMutation, ActivateRevisionMutationVariables>;
export const DeleteRevisionDocument = gql`
    mutation DeleteRevision($revisionId: ID!) {
  deleteRevision(revisionId: $revisionId)
}
    `;

/**
 * __useDeleteRevisionMutation__
 *
 * To run a mutation, you first call `useDeleteRevisionMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useDeleteRevisionMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useDeleteRevisionMutation({
 *   variables: {
 *     revisionId: // value for 'revisionId'
 *   },
 * });
 */
export function useDeleteRevisionMutation(options: VueApolloComposable.UseMutationOptions<DeleteRevisionMutation, DeleteRevisionMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<DeleteRevisionMutation, DeleteRevisionMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<DeleteRevisionMutation, DeleteRevisionMutationVariables>(DeleteRevisionDocument, options);
}
export type DeleteRevisionMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<DeleteRevisionMutation, DeleteRevisionMutationVariables>;
export const UpdateFeedDocument = gql`
    mutation UpdateFeed($code: String!, $input: UpdateFeedInput!) {
  updateFeed(code: $code, input: $input) {
    code
    name
    description
    url
    pollingCron
    enabled
  }
}
    `;

/**
 * __useUpdateFeedMutation__
 *
 * To run a mutation, you first call `useUpdateFeedMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useUpdateFeedMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useUpdateFeedMutation({
 *   variables: {
 *     code: // value for 'code'
 *     input: // value for 'input'
 *   },
 * });
 */
export function useUpdateFeedMutation(options: VueApolloComposable.UseMutationOptions<UpdateFeedMutation, UpdateFeedMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<UpdateFeedMutation, UpdateFeedMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<UpdateFeedMutation, UpdateFeedMutationVariables>(UpdateFeedDocument, options);
}
export type UpdateFeedMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<UpdateFeedMutation, UpdateFeedMutationVariables>;
export const FeedReadinessDocument = gql`
    query FeedReadiness($code: String!) {
  feed(code: $code) {
    code
    name
    activeRevision {
      id
    }
    revisions {
      id
      status
    }
  }
}
    `;

/**
 * __useFeedReadinessQuery__
 *
 * To run a query within a Vue component, call `useFeedReadinessQuery` and pass it any options that fit your needs.
 * When your component renders, `useFeedReadinessQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useFeedReadinessQuery({
 *   code: // value for 'code'
 * });
 */
export function useFeedReadinessQuery(variables: FeedReadinessQueryVariables | VueCompositionApi.Ref<FeedReadinessQueryVariables> | ReactiveFunction<FeedReadinessQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<FeedReadinessQuery, FeedReadinessQueryVariables>(FeedReadinessDocument, variables, options);
}
export function useFeedReadinessLazyQuery(variables?: FeedReadinessQueryVariables | VueCompositionApi.Ref<FeedReadinessQueryVariables> | ReactiveFunction<FeedReadinessQueryVariables>, options: VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedReadinessQuery, FeedReadinessQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<FeedReadinessQuery, FeedReadinessQueryVariables>(FeedReadinessDocument, variables, options);
}
export type FeedReadinessQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<FeedReadinessQuery, FeedReadinessQueryVariables>;
export const FeedsDocument = gql`
    query Feeds {
  feeds {
    code
    name
    description
    enabled
    source
    activeRevision {
      id
      status
      feedStartDate
      feedEndDate
    }
  }
}
    `;

/**
 * __useFeedsQuery__
 *
 * To run a query within a Vue component, call `useFeedsQuery` and pass it any options that fit your needs.
 * When your component renders, `useFeedsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useFeedsQuery();
 */
export function useFeedsQuery(options: VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<FeedsQuery, FeedsQueryVariables>(FeedsDocument, {}, options);
}
export function useFeedsLazyQuery(options: VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<FeedsQuery, FeedsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<FeedsQuery, FeedsQueryVariables>(FeedsDocument, {}, options);
}
export type FeedsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<FeedsQuery, FeedsQueryVariables>;
export const HeadwayInfoDocument = gql`
    query HeadwayInfo($feedCode: String!, $stopId: String!, $routeId: String!, $directionId: Int, $algorithm: PredictionAlgorithm) {
  headway(
    feedCode: $feedCode
    stopId: $stopId
    routeId: $routeId
    directionId: $directionId
    algorithm: $algorithm
  ) {
    stopId
    routeId
    directionId
    waitSec
    gapsSec
    scheduledHeadwaySec
  }
}
    `;

/**
 * __useHeadwayInfoQuery__
 *
 * To run a query within a Vue component, call `useHeadwayInfoQuery` and pass it any options that fit your needs.
 * When your component renders, `useHeadwayInfoQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useHeadwayInfoQuery({
 *   feedCode: // value for 'feedCode'
 *   stopId: // value for 'stopId'
 *   routeId: // value for 'routeId'
 *   directionId: // value for 'directionId'
 *   algorithm: // value for 'algorithm'
 * });
 */
export function useHeadwayInfoQuery(variables: HeadwayInfoQueryVariables | VueCompositionApi.Ref<HeadwayInfoQueryVariables> | ReactiveFunction<HeadwayInfoQueryVariables>, options: VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<HeadwayInfoQuery, HeadwayInfoQueryVariables>(HeadwayInfoDocument, variables, options);
}
export function useHeadwayInfoLazyQuery(variables?: HeadwayInfoQueryVariables | VueCompositionApi.Ref<HeadwayInfoQueryVariables> | ReactiveFunction<HeadwayInfoQueryVariables>, options: VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<HeadwayInfoQuery, HeadwayInfoQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<HeadwayInfoQuery, HeadwayInfoQueryVariables>(HeadwayInfoDocument, variables, options);
}
export type HeadwayInfoQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<HeadwayInfoQuery, HeadwayInfoQueryVariables>;
export const HeadwayRoutePatternsDocument = gql`
    query HeadwayRoutePatterns($feedCode: String!, $routeId: String!) {
  tripPatterns(feedCode: $feedCode, routeId: $routeId) {
    patternKey
    directionId
    stopPaths {
      stopPathIndex
      stopId
      stop {
        stopName
      }
    }
  }
}
    `;

/**
 * __useHeadwayRoutePatternsQuery__
 *
 * To run a query within a Vue component, call `useHeadwayRoutePatternsQuery` and pass it any options that fit your needs.
 * When your component renders, `useHeadwayRoutePatternsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useHeadwayRoutePatternsQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 * });
 */
export function useHeadwayRoutePatternsQuery(variables: HeadwayRoutePatternsQueryVariables | VueCompositionApi.Ref<HeadwayRoutePatternsQueryVariables> | ReactiveFunction<HeadwayRoutePatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>(HeadwayRoutePatternsDocument, variables, options);
}
export function useHeadwayRoutePatternsLazyQuery(variables?: HeadwayRoutePatternsQueryVariables | VueCompositionApi.Ref<HeadwayRoutePatternsQueryVariables> | ReactiveFunction<HeadwayRoutePatternsQueryVariables>, options: VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>(HeadwayRoutePatternsDocument, variables, options);
}
export type HeadwayRoutePatternsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables>;
export const StartOptimizationRunDocument = gql`
    mutation StartOptimizationRun($input: OptimizationRunInput!) {
  startOptimizationRun(input: $input) {
    id
    revisionId
    state
    error
    createdAt
    completedAt
  }
}
    `;

/**
 * __useStartOptimizationRunMutation__
 *
 * To run a mutation, you first call `useStartOptimizationRunMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useStartOptimizationRunMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useStartOptimizationRunMutation({
 *   variables: {
 *     input: // value for 'input'
 *   },
 * });
 */
export function useStartOptimizationRunMutation(options: VueApolloComposable.UseMutationOptions<StartOptimizationRunMutation, StartOptimizationRunMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>(StartOptimizationRunDocument, options);
}
export type StartOptimizationRunMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<StartOptimizationRunMutation, StartOptimizationRunMutationVariables>;
export const ApplyOptimizationRecommendationsDocument = gql`
    mutation ApplyOptimizationRecommendations($runId: ID!, $recommendationIds: [ID!]!, $label: String, $editor: String!) {
  applyOptimizationRecommendations(
    runId: $runId
    recommendationIds: $recommendationIds
    label: $label
    editor: $editor
  ) {
    id
    feedCode
    label
    status
    kind
  }
}
    `;

/**
 * __useApplyOptimizationRecommendationsMutation__
 *
 * To run a mutation, you first call `useApplyOptimizationRecommendationsMutation` within a Vue component and pass it any options that fit your needs.
 * When your component renders, `useApplyOptimizationRecommendationsMutation` returns an object that includes:
 * - A mutate function that you can call at any time to execute the mutation
 * - Several other properties: https://v4.apollo.vuejs.org/api/use-mutation.html#return
 *
 * @param options that will be passed into the mutation, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/mutation.html#options;
 *
 * @example
 * const { mutate, loading, error, onDone } = useApplyOptimizationRecommendationsMutation({
 *   variables: {
 *     runId: // value for 'runId'
 *     recommendationIds: // value for 'recommendationIds'
 *     label: // value for 'label'
 *     editor: // value for 'editor'
 *   },
 * });
 */
export function useApplyOptimizationRecommendationsMutation(options: VueApolloComposable.UseMutationOptions<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables> | ReactiveFunction<VueApolloComposable.UseMutationOptions<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>> = {}) {
  return VueApolloComposable.useMutation<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>(ApplyOptimizationRecommendationsDocument, options);
}
export type ApplyOptimizationRecommendationsMutationCompositionFunctionResult = VueApolloComposable.UseMutationReturn<ApplyOptimizationRecommendationsMutation, ApplyOptimizationRecommendationsMutationVariables>;
export const OptimizationRunsDocument = gql`
    query OptimizationRuns($feedCode: String!, $limit: Int, $offset: Int) {
  optimizationRuns(feedCode: $feedCode, limit: $limit, offset: $offset) {
    id
    revisionId
    state
    error
    createdAt
    completedAt
  }
}
    `;

/**
 * __useOptimizationRunsQuery__
 *
 * To run a query within a Vue component, call `useOptimizationRunsQuery` and pass it any options that fit your needs.
 * When your component renders, `useOptimizationRunsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useOptimizationRunsQuery({
 *   feedCode: // value for 'feedCode'
 *   limit: // value for 'limit'
 *   offset: // value for 'offset'
 * });
 */
export function useOptimizationRunsQuery(variables: OptimizationRunsQueryVariables | VueCompositionApi.Ref<OptimizationRunsQueryVariables> | ReactiveFunction<OptimizationRunsQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<OptimizationRunsQuery, OptimizationRunsQueryVariables>(OptimizationRunsDocument, variables, options);
}
export function useOptimizationRunsLazyQuery(variables?: OptimizationRunsQueryVariables | VueCompositionApi.Ref<OptimizationRunsQueryVariables> | ReactiveFunction<OptimizationRunsQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRunsQuery, OptimizationRunsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<OptimizationRunsQuery, OptimizationRunsQueryVariables>(OptimizationRunsDocument, variables, options);
}
export type OptimizationRunsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<OptimizationRunsQuery, OptimizationRunsQueryVariables>;
export const OptimizationRunDocument = gql`
    query OptimizationRun($id: ID!) {
  optimizationRun(id: $id) {
    id
    revisionId
    state
    error
    createdAt
    completedAt
  }
}
    `;

/**
 * __useOptimizationRunQuery__
 *
 * To run a query within a Vue component, call `useOptimizationRunQuery` and pass it any options that fit your needs.
 * When your component renders, `useOptimizationRunQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useOptimizationRunQuery({
 *   id: // value for 'id'
 * });
 */
export function useOptimizationRunQuery(variables: OptimizationRunQueryVariables | VueCompositionApi.Ref<OptimizationRunQueryVariables> | ReactiveFunction<OptimizationRunQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<OptimizationRunQuery, OptimizationRunQueryVariables>(OptimizationRunDocument, variables, options);
}
export function useOptimizationRunLazyQuery(variables?: OptimizationRunQueryVariables | VueCompositionApi.Ref<OptimizationRunQueryVariables> | ReactiveFunction<OptimizationRunQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRunQuery, OptimizationRunQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<OptimizationRunQuery, OptimizationRunQueryVariables>(OptimizationRunDocument, variables, options);
}
export type OptimizationRunQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<OptimizationRunQuery, OptimizationRunQueryVariables>;
export const OptimizationRecommendationsDocument = gql`
    query OptimizationRecommendations($runId: ID!, $status: OptimizationRecommendationStatus, $offset: Int, $limit: Int) {
  optimizationRecommendations(
    runId: $runId
    status: $status
    offset: $offset
    limit: $limit
  ) {
    id
    kind
    status
    sampleCount
    deltaSec
    currentValue
    proposedValue
    evidence
    reason
  }
}
    `;

/**
 * __useOptimizationRecommendationsQuery__
 *
 * To run a query within a Vue component, call `useOptimizationRecommendationsQuery` and pass it any options that fit your needs.
 * When your component renders, `useOptimizationRecommendationsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useOptimizationRecommendationsQuery({
 *   runId: // value for 'runId'
 *   status: // value for 'status'
 *   offset: // value for 'offset'
 *   limit: // value for 'limit'
 * });
 */
export function useOptimizationRecommendationsQuery(variables: OptimizationRecommendationsQueryVariables | VueCompositionApi.Ref<OptimizationRecommendationsQueryVariables> | ReactiveFunction<OptimizationRecommendationsQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>(OptimizationRecommendationsDocument, variables, options);
}
export function useOptimizationRecommendationsLazyQuery(variables?: OptimizationRecommendationsQueryVariables | VueCompositionApi.Ref<OptimizationRecommendationsQueryVariables> | ReactiveFunction<OptimizationRecommendationsQueryVariables>, options: VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>(OptimizationRecommendationsDocument, variables, options);
}
export type OptimizationRecommendationsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<OptimizationRecommendationsQuery, OptimizationRecommendationsQueryVariables>;
export const PredictionAccuracyDocument = gql`
    query PredictionAccuracy($feedCode: String!, $sinceDays: Int) {
  predictionAccuracy(feedCode: $feedCode, sinceDays: $sinceDays) {
    algorithm
    sampleCount
    meanErrorSec
    meanAbsErrorSec
  }
}
    `;

/**
 * __usePredictionAccuracyQuery__
 *
 * To run a query within a Vue component, call `usePredictionAccuracyQuery` and pass it any options that fit your needs.
 * When your component renders, `usePredictionAccuracyQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = usePredictionAccuracyQuery({
 *   feedCode: // value for 'feedCode'
 *   sinceDays: // value for 'sinceDays'
 * });
 */
export function usePredictionAccuracyQuery(variables: PredictionAccuracyQueryVariables | VueCompositionApi.Ref<PredictionAccuracyQueryVariables> | ReactiveFunction<PredictionAccuracyQueryVariables>, options: VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>(PredictionAccuracyDocument, variables, options);
}
export function usePredictionAccuracyLazyQuery(variables?: PredictionAccuracyQueryVariables | VueCompositionApi.Ref<PredictionAccuracyQueryVariables> | ReactiveFunction<PredictionAccuracyQueryVariables>, options: VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>(PredictionAccuracyDocument, variables, options);
}
export type PredictionAccuracyQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<PredictionAccuracyQuery, PredictionAccuracyQueryVariables>;
export const RevisionsDocument = gql`
    query Revisions($feedCode: String!) {
  revisions(feedCode: $feedCode) {
    id
    status
    createdAt
    activatedAt
  }
}
    `;

/**
 * __useRevisionsQuery__
 *
 * To run a query within a Vue component, call `useRevisionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useRevisionsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useRevisionsQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useRevisionsQuery(variables: RevisionsQueryVariables | VueCompositionApi.Ref<RevisionsQueryVariables> | ReactiveFunction<RevisionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<RevisionsQuery, RevisionsQueryVariables>(RevisionsDocument, variables, options);
}
export function useRevisionsLazyQuery(variables?: RevisionsQueryVariables | VueCompositionApi.Ref<RevisionsQueryVariables> | ReactiveFunction<RevisionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RevisionsQuery, RevisionsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<RevisionsQuery, RevisionsQueryVariables>(RevisionsDocument, variables, options);
}
export type RevisionsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<RevisionsQuery, RevisionsQueryVariables>;
export const RouteDetailDocument = gql`
    query RouteDetail($feedCode: String!, $routeId: String!) {
  route(feedCode: $feedCode, routeId: $routeId) {
    routeId
    routeShortName
    routeLongName
    routeDesc
    routeType
    routeColor
    routeTextColor
    agencyId
  }
  tripPatterns(feedCode: $feedCode, routeId: $routeId) {
    patternKey
    directionId
    headsign
    shapeId
    stopCount
    tripCount
    lengthM
    stopPaths {
      stopPathIndex
      stopId
      lengthM
      pathGeometry
      pickupType
      dropOffType
      waitStop
      scheduleAdherenceStop
      layoverStop
      breakTimeSec
      typicalTravelTimeSec
      typicalDwellTimeSec
      stop {
        stopId
        stopName
        stopLat
        stopLon
      }
    }
  }
}
    `;

/**
 * __useRouteDetailQuery__
 *
 * To run a query within a Vue component, call `useRouteDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useRouteDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useRouteDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 * });
 */
export function useRouteDetailQuery(variables: RouteDetailQueryVariables | VueCompositionApi.Ref<RouteDetailQueryVariables> | ReactiveFunction<RouteDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<RouteDetailQuery, RouteDetailQueryVariables>(RouteDetailDocument, variables, options);
}
export function useRouteDetailLazyQuery(variables?: RouteDetailQueryVariables | VueCompositionApi.Ref<RouteDetailQueryVariables> | ReactiveFunction<RouteDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RouteDetailQuery, RouteDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<RouteDetailQuery, RouteDetailQueryVariables>(RouteDetailDocument, variables, options);
}
export type RouteDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<RouteDetailQuery, RouteDetailQueryVariables>;
export const RoutesDocument = gql`
    query Routes($feedCode: String!) {
  routes(feedCode: $feedCode) {
    routeId
    routeShortName
    routeLongName
  }
}
    `;

/**
 * __useRoutesQuery__
 *
 * To run a query within a Vue component, call `useRoutesQuery` and pass it any options that fit your needs.
 * When your component renders, `useRoutesQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useRoutesQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useRoutesQuery(variables: RoutesQueryVariables | VueCompositionApi.Ref<RoutesQueryVariables> | ReactiveFunction<RoutesQueryVariables>, options: VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<RoutesQuery, RoutesQueryVariables>(RoutesDocument, variables, options);
}
export function useRoutesLazyQuery(variables?: RoutesQueryVariables | VueCompositionApi.Ref<RoutesQueryVariables> | ReactiveFunction<RoutesQueryVariables>, options: VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<RoutesQuery, RoutesQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<RoutesQuery, RoutesQueryVariables>(RoutesDocument, variables, options);
}
export type RoutesQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<RoutesQuery, RoutesQueryVariables>;
export const StopDetailDocument = gql`
    query StopDetail($feedCode: String!, $stopId: String!) {
  stop(feedCode: $feedCode, stopId: $stopId) {
    stopId
    stopName
    stopCode
    stopDesc
    stopLat
    stopLon
    zoneId
    locationType
    parentStation
    wheelchairBoarding
    platformCode
  }
}
    `;

/**
 * __useStopDetailQuery__
 *
 * To run a query within a Vue component, call `useStopDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useStopDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useStopDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   stopId: // value for 'stopId'
 * });
 */
export function useStopDetailQuery(variables: StopDetailQueryVariables | VueCompositionApi.Ref<StopDetailQueryVariables> | ReactiveFunction<StopDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<StopDetailQuery, StopDetailQueryVariables>(StopDetailDocument, variables, options);
}
export function useStopDetailLazyQuery(variables?: StopDetailQueryVariables | VueCompositionApi.Ref<StopDetailQueryVariables> | ReactiveFunction<StopDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopDetailQuery, StopDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<StopDetailQuery, StopDetailQueryVariables>(StopDetailDocument, variables, options);
}
export type StopDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<StopDetailQuery, StopDetailQueryVariables>;
export const StopBoardDocument = gql`
    query StopBoard($feedCode: String!, $stopId: String!, $routeId: String, $directionId: Int, $algorithm: PredictionAlgorithm) {
  stopPredictions(
    feedCode: $feedCode
    stopId: $stopId
    routeId: $routeId
    directionId: $directionId
    algorithm: $algorithm
  ) {
    stopPathIndex
    scheduledArrival
    predictedArrival
    actualArrival
    algorithm
    confidenceSec
  }
}
    `;

/**
 * __useStopBoardQuery__
 *
 * To run a query within a Vue component, call `useStopBoardQuery` and pass it any options that fit your needs.
 * When your component renders, `useStopBoardQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useStopBoardQuery({
 *   feedCode: // value for 'feedCode'
 *   stopId: // value for 'stopId'
 *   routeId: // value for 'routeId'
 *   directionId: // value for 'directionId'
 *   algorithm: // value for 'algorithm'
 * });
 */
export function useStopBoardQuery(variables: StopBoardQueryVariables | VueCompositionApi.Ref<StopBoardQueryVariables> | ReactiveFunction<StopBoardQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<StopBoardQuery, StopBoardQueryVariables>(StopBoardDocument, variables, options);
}
export function useStopBoardLazyQuery(variables?: StopBoardQueryVariables | VueCompositionApi.Ref<StopBoardQueryVariables> | ReactiveFunction<StopBoardQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopBoardQuery, StopBoardQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<StopBoardQuery, StopBoardQueryVariables>(StopBoardDocument, variables, options);
}
export type StopBoardQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<StopBoardQuery, StopBoardQueryVariables>;
export const StopsDocument = gql`
    query Stops($feedCode: String!) {
  stops(feedCode: $feedCode) {
    stopId
    stopName
    stopCode
  }
}
    `;

/**
 * __useStopsQuery__
 *
 * To run a query within a Vue component, call `useStopsQuery` and pass it any options that fit your needs.
 * When your component renders, `useStopsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useStopsQuery({
 *   feedCode: // value for 'feedCode'
 * });
 */
export function useStopsQuery(variables: StopsQueryVariables | VueCompositionApi.Ref<StopsQueryVariables> | ReactiveFunction<StopsQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<StopsQuery, StopsQueryVariables>(StopsDocument, variables, options);
}
export function useStopsLazyQuery(variables?: StopsQueryVariables | VueCompositionApi.Ref<StopsQueryVariables> | ReactiveFunction<StopsQueryVariables>, options: VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<StopsQuery, StopsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<StopsQuery, StopsQueryVariables>(StopsDocument, variables, options);
}
export type StopsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<StopsQuery, StopsQueryVariables>;
export const TripDetailDocument = gql`
    query TripDetail($feedCode: String!, $tripId: String!) {
  trip(feedCode: $feedCode, tripId: $tripId) {
    tripId
    tripHeadsign
    routeId
    directionId
    serviceId
    shapeId
    startTimeSec
    endTimeSec
    frequencyBased
    noSchedule
    route {
      routeShortName
      routeLongName
      routeColor
      routeTextColor
    }
    pattern {
      patternKey
      stopCount
      lengthM
    }
    block {
      blockId
      serviceId
    }
    scheduleTimes {
      stopPathIndex
      arrivalSec
      departureSec
      interpolated
      schedTravelTimeSec
      schedDwellTimeSec
    }
    stopTimes {
      stopSequence
      arrivalTime
      departureTime
      stop {
        stopId
        stopName
        stopLat
        stopLon
      }
    }
    shape {
      points {
        lat
        lon
      }
    }
  }
}
    `;

/**
 * __useTripDetailQuery__
 *
 * To run a query within a Vue component, call `useTripDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useTripDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useTripDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   tripId: // value for 'tripId'
 * });
 */
export function useTripDetailQuery(variables: TripDetailQueryVariables | VueCompositionApi.Ref<TripDetailQueryVariables> | ReactiveFunction<TripDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<TripDetailQuery, TripDetailQueryVariables>(TripDetailDocument, variables, options);
}
export function useTripDetailLazyQuery(variables?: TripDetailQueryVariables | VueCompositionApi.Ref<TripDetailQueryVariables> | ReactiveFunction<TripDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripDetailQuery, TripDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<TripDetailQuery, TripDetailQueryVariables>(TripDetailDocument, variables, options);
}
export type TripDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<TripDetailQuery, TripDetailQueryVariables>;
export const TripsByRouteDocument = gql`
    query TripsByRoute($feedCode: String!, $routeId: String!) {
  trips(feedCode: $feedCode, routeId: $routeId) {
    tripId
    tripHeadsign
    tripShortName
    directionId
    serviceId
    shapeId
    blockId
    startTimeSec
    endTimeSec
  }
}
    `;

/**
 * __useTripsByRouteQuery__
 *
 * To run a query within a Vue component, call `useTripsByRouteQuery` and pass it any options that fit your needs.
 * When your component renders, `useTripsByRouteQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useTripsByRouteQuery({
 *   feedCode: // value for 'feedCode'
 *   routeId: // value for 'routeId'
 * });
 */
export function useTripsByRouteQuery(variables: TripsByRouteQueryVariables | VueCompositionApi.Ref<TripsByRouteQueryVariables> | ReactiveFunction<TripsByRouteQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<TripsByRouteQuery, TripsByRouteQueryVariables>(TripsByRouteDocument, variables, options);
}
export function useTripsByRouteLazyQuery(variables?: TripsByRouteQueryVariables | VueCompositionApi.Ref<TripsByRouteQueryVariables> | ReactiveFunction<TripsByRouteQueryVariables>, options: VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<TripsByRouteQuery, TripsByRouteQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<TripsByRouteQuery, TripsByRouteQueryVariables>(TripsByRouteDocument, variables, options);
}
export type TripsByRouteQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<TripsByRouteQuery, TripsByRouteQueryVariables>;
export const VehicleDetailDocument = gql`
    query VehicleDetail($feedCode: String!, $vehicleId: String!) {
  vehicle(feedCode: $feedCode, vehicleId: $vehicleId) {
    vehicleId
    label
    reportTs
    position {
      lat
      lon
    }
    snappedPosition {
      lat
      lon
    }
    bearing
    speedMps
    occupancyStatus
    matched
    stale
    scheduleAdherenceSec
    stopPathIndex
    distanceAlongTripM
    currentStop {
      stopId
      stopName
      stopLat
      stopLon
    }
    block {
      blockId
      serviceId
      startTimeSec
      endTimeSec
      blockTrips {
        listIndex
        trip {
          tripId
          tripHeadsign
        }
      }
    }
    trip {
      tripId
      tripHeadsign
      directionId
      routeId
      startTimeSec
      endTimeSec
      route {
        routeShortName
        routeLongName
        routeColor
        routeTextColor
      }
      shape {
        points {
          lat
          lon
        }
      }
    }
    pattern {
      patternKey
      lengthM
      stopPaths {
        stopPathIndex
        stopId
        waitStop
        stop {
          stopId
          stopName
          stopLat
          stopLon
        }
        pathGeometry
      }
    }
  }
  vehiclePredictions(feedCode: $feedCode, vehicleId: $vehicleId) {
    stopPathIndex
    stop {
      stopId
      stopName
    }
    scheduledArrival
    scheduledDeparture
    predictedArrival
    predictedDeparture
    actualArrival
    actualDeparture
    actualReportGapSec
    actualReliable
    sourceReportTs
    algorithm
    confidenceSec
  }
  predictionAccuracy(feedCode: $feedCode) {
    algorithm
    sampleCount
    meanErrorSec
    meanAbsErrorSec
    eligibleCount
    predictionCoverage
    pairedSampleCount
    p90AbsErrorSec
    p95AbsErrorSec
    confidenceCoverage
    confidenceSampleCount
  }
}
    `;

/**
 * __useVehicleDetailQuery__
 *
 * To run a query within a Vue component, call `useVehicleDetailQuery` and pass it any options that fit your needs.
 * When your component renders, `useVehicleDetailQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useVehicleDetailQuery({
 *   feedCode: // value for 'feedCode'
 *   vehicleId: // value for 'vehicleId'
 * });
 */
export function useVehicleDetailQuery(variables: VehicleDetailQueryVariables | VueCompositionApi.Ref<VehicleDetailQueryVariables> | ReactiveFunction<VehicleDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<VehicleDetailQuery, VehicleDetailQueryVariables>(VehicleDetailDocument, variables, options);
}
export function useVehicleDetailLazyQuery(variables?: VehicleDetailQueryVariables | VueCompositionApi.Ref<VehicleDetailQueryVariables> | ReactiveFunction<VehicleDetailQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehicleDetailQuery, VehicleDetailQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<VehicleDetailQuery, VehicleDetailQueryVariables>(VehicleDetailDocument, variables, options);
}
export type VehicleDetailQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<VehicleDetailQuery, VehicleDetailQueryVariables>;
export const VehiclesDocument = gql`
    query Vehicles($feedCode: String!, $matchedOnly: Boolean) {
  vehicles(feedCode: $feedCode, matchedOnly: $matchedOnly) {
    vehicleId
    label
    reportTs
    position {
      lat
      lon
    }
    bearing
    matched
    stale
    scheduleAdherenceSec
    stopPathIndex
    distanceAlongTripM
    speedMps
    pattern {
      lengthM
    }
    trip {
      routeId
      tripHeadsign
      route {
        routeShortName
        routeColor
        routeTextColor
      }
    }
  }
}
    `;

/**
 * __useVehiclesQuery__
 *
 * To run a query within a Vue component, call `useVehiclesQuery` and pass it any options that fit your needs.
 * When your component renders, `useVehiclesQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useVehiclesQuery({
 *   feedCode: // value for 'feedCode'
 *   matchedOnly: // value for 'matchedOnly'
 * });
 */
export function useVehiclesQuery(variables: VehiclesQueryVariables | VueCompositionApi.Ref<VehiclesQueryVariables> | ReactiveFunction<VehiclesQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<VehiclesQuery, VehiclesQueryVariables>(VehiclesDocument, variables, options);
}
export function useVehiclesLazyQuery(variables?: VehiclesQueryVariables | VueCompositionApi.Ref<VehiclesQueryVariables> | ReactiveFunction<VehiclesQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehiclesQuery, VehiclesQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<VehiclesQuery, VehiclesQueryVariables>(VehiclesDocument, variables, options);
}
export type VehiclesQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<VehiclesQuery, VehiclesQueryVariables>;