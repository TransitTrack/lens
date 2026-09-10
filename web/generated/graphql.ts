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
  JSON: { input: any; output: any; }
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
  stops: Array<NewStopTimeInput>;
  tripId?: InputMaybe<Scalars['String']['input']>;
};

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

export type AvlFeed = {
  __typename?: 'AvlFeed';
  assignmentMode: Scalars['String']['output'];
  code: Scalars['String']['output'];
  enabled: Scalars['Boolean']['output'];
  format: Scalars['String']['output'];
  gtfsFeedCode: Scalars['String']['output'];
  lastPollAt?: Maybe<Scalars['String']['output']>;
  lastPollReportCount?: Maybe<Scalars['Int']['output']>;
  lastPollStatus?: Maybe<Scalars['String']['output']>;
  name: Scalars['String']['output'];
  pollIntervalSec: Scalars['Int']['output'];
};

export type AvlReport = {
  __typename?: 'AvlReport';
  bearing?: Maybe<Scalars['Float']['output']>;
  currentStatus?: Maybe<Scalars['String']['output']>;
  descTripId?: Maybe<Scalars['String']['output']>;
  occupancyStatus?: Maybe<Scalars['String']['output']>;
  position: LatLon;
  speedMps?: Maybe<Scalars['Float']['output']>;
  ts: Scalars['String']['output'];
  vehicleId: Scalars['String']['output'];
};

export type Block = {
  __typename?: 'Block';
  blockId: Scalars['String']['output'];
  blockTrips: Array<BlockTrip>;
  endTimeSec: Scalars['Int']['output'];
  id: Scalars['ID']['output'];
  routeIds: Array<Scalars['String']['output']>;
  serviceId: Scalars['String']['output'];
  startTimeSec: Scalars['Int']['output'];
  tripCount: Scalars['Int']['output'];
  trips: Array<Trip>;
};

export type BlockTrip = {
  __typename?: 'BlockTrip';
  deadheadAfter?: Maybe<Scalars['Boolean']['output']>;
  layoverAfterSec?: Maybe<Scalars['Int']['output']>;
  listIndex: Scalars['Int']['output'];
  trip: Trip;
};

export type BulkShiftTripsInput = {
  deltaSec: Scalars['Int']['input'];
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  patternKey?: InputMaybe<Scalars['String']['input']>;
  routeId?: InputMaybe<Scalars['String']['input']>;
  serviceId?: InputMaybe<Scalars['String']['input']>;
  windowFromSec?: InputMaybe<Scalars['Int']['input']>;
  windowToSec?: InputMaybe<Scalars['Int']['input']>;
};

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

export type CalendarDate = {
  __typename?: 'CalendarDate';
  date: Scalars['String']['output'];
  exceptionType?: Maybe<Scalars['Int']['output']>;
  serviceId: Scalars['String']['output'];
};

export type DeleteTripInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  tripId: Scalars['String']['input'];
};

export type Draft = {
  __typename?: 'Draft';
  baseRevisionId?: Maybe<Scalars['ID']['output']>;
  createdAt: Scalars['String']['output'];
  createdBy?: Maybe<Scalars['String']['output']>;
  derivationStale: Scalars['Boolean']['output'];
  feedCode: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  kind: Scalars['String']['output'];
  label?: Maybe<Scalars['String']['output']>;
  lastValidation?: Maybe<Scalars['JSON']['output']>;
  lock?: Maybe<DraftLock>;
  rowCounts?: Maybe<Scalars['JSON']['output']>;
  status: Scalars['String']['output'];
  version: Scalars['Long']['output'];
};

export type DraftEdit = {
  __typename?: 'DraftEdit';
  appliedAt: Scalars['String']['output'];
  op: Scalars['String']['output'];
  seq: Scalars['Int']['output'];
  summary: Scalars['String']['output'];
  undone: Scalars['Boolean']['output'];
};

export type DraftEditResult = {
  __typename?: 'DraftEditResult';
  canRedo: Scalars['Boolean']['output'];
  canUndo: Scalars['Boolean']['output'];
  draft: Draft;
  edit?: Maybe<DraftEdit>;
};

export type DraftGrid = {
  __typename?: 'DraftGrid';
  stops: Array<DraftGridStop>;
  trips: Array<DraftGridTrip>;
};

export type DraftGridCell = {
  __typename?: 'DraftGridCell';
  arrivalSec?: Maybe<Scalars['Int']['output']>;
  departureSec?: Maybe<Scalars['Int']['output']>;
  stopSequence: Scalars['Int']['output'];
};

export type DraftGridStop = {
  __typename?: 'DraftGridStop';
  stopId?: Maybe<Scalars['String']['output']>;
  stopName?: Maybe<Scalars['String']['output']>;
  stopSequence: Scalars['Int']['output'];
  timepoint?: Maybe<Scalars['Int']['output']>;
};

export type DraftGridTrip = {
  __typename?: 'DraftGridTrip';
  blockId?: Maybe<Scalars['String']['output']>;
  cells: Array<DraftGridCell>;
  directionId?: Maybe<Scalars['Int']['output']>;
  firstDepartureSec?: Maybe<Scalars['Int']['output']>;
  headsign?: Maybe<Scalars['String']['output']>;
  serviceId?: Maybe<Scalars['String']['output']>;
  tripId: Scalars['String']['output'];
};

export type DraftJob = {
  __typename?: 'DraftJob';
  error?: Maybe<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  phase: Scalars['String']['output'];
  state: Scalars['String']['output'];
};

export type DraftLock = {
  __typename?: 'DraftLock';
  editor: Scalars['String']['output'];
  expiresAt: Scalars['String']['output'];
};

export type DuplicateTripInput = {
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  newTripId?: InputMaybe<Scalars['String']['input']>;
  offsetSec: Scalars['Int']['input'];
  sourceTripId: Scalars['String']['input'];
};

export type Extent = {
  __typename?: 'Extent';
  maxLat: Scalars['Float']['output'];
  maxLon: Scalars['Float']['output'];
  minLat: Scalars['Float']['output'];
  minLon: Scalars['Float']['output'];
};

export type Feed = {
  __typename?: 'Feed';
  activeRevision?: Maybe<Revision>;
  code: Scalars['String']['output'];
  description?: Maybe<Scalars['String']['output']>;
  enabled: Scalars['Boolean']['output'];
  name: Scalars['String']['output'];
  pollingCron?: Maybe<Scalars['String']['output']>;
  revisions: Array<Revision>;
  source: Scalars['String']['output'];
  url: Scalars['String']['output'];
};

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

export type ForkDraftInput = {
  baseRevisionId?: InputMaybe<Scalars['ID']['input']>;
  editor: Scalars['String']['input'];
  feedCode: Scalars['String']['input'];
  label?: InputMaybe<Scalars['String']['input']>;
};

export type Frequency = {
  __typename?: 'Frequency';
  endTime?: Maybe<Scalars['String']['output']>;
  exactTimes?: Maybe<Scalars['Int']['output']>;
  headwaySecs?: Maybe<Scalars['Int']['output']>;
  startTime: Scalars['String']['output'];
  tripId: Scalars['String']['output'];
};

export type Headway = {
  __typename?: 'Headway';
  directionId?: Maybe<Scalars['Int']['output']>;
  gapsSec: Array<Scalars['Int']['output']>;
  routeId: Scalars['String']['output'];
  scheduledHeadwaySec?: Maybe<Scalars['Int']['output']>;
  stopId: Scalars['String']['output'];
  waitSec?: Maybe<Scalars['Int']['output']>;
};

export type InsertTripStopInput = {
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

export type Level = {
  __typename?: 'Level';
  levelId: Scalars['String']['output'];
  levelIndex?: Maybe<Scalars['Float']['output']>;
  levelName?: Maybe<Scalars['String']['output']>;
};

export type Mutation = {
  __typename?: 'Mutation';
  activateDraft: Revision;
  activateRevision: Revision;
  addTrip: DraftEditResult;
  bulkShiftTrips: DraftEditResult;
  claimDraftEditor: DraftLock;
  deleteFeed: Scalars['Boolean']['output'];
  deleteRevision: Scalars['Boolean']['output'];
  deleteTrip: DraftEditResult;
  discardDraft: Scalars['Boolean']['output'];
  duplicateTrip: DraftEditResult;
  forkDraft: Draft;
  ingestFeed: Revision;
  insertTripStop: DraftEditResult;
  rebuildDraft: DraftJob;
  redoDraftEdit: DraftEditResult;
  registerFeed: Feed;
  releaseDraftEditor: Scalars['Boolean']['output'];
  removeTripStop: DraftEditResult;
  renewDraftEditor: DraftLock;
  reorderTripStops: DraftEditResult;
  revertDraftToFork: Draft;
  setStopDwell: DraftEditResult;
  shiftTrip: DraftEditResult;
  undoDraftEdit: DraftEditResult;
  updateFeed: Feed;
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


export type MutationSetStopDwellArgs = {
  input: SetStopDwellInput;
};


export type MutationShiftTripArgs = {
  input: ShiftTripInput;
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

export type NewStopTimeInput = {
  arrivalSec: Scalars['Int']['input'];
  departureSec: Scalars['Int']['input'];
  stopId: Scalars['String']['input'];
};

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

export type PredictionAccuracySummary = {
  __typename?: 'PredictionAccuracySummary';
  algorithm: Scalars['String']['output'];
  meanAbsErrorSec: Scalars['Float']['output'];
  meanErrorSec: Scalars['Float']['output'];
  sampleCount: Scalars['Int']['output'];
};

export type Query = {
  __typename?: 'Query';
  agencies: Array<Agency>;
  avlFeeds: Array<AvlFeed>;
  avlReports: Array<AvlReport>;
  block?: Maybe<Block>;
  blocks: Array<Block>;
  blocksOnDate: Array<Block>;
  calendarDates: Array<CalendarDate>;
  calendars: Array<Calendar>;
  draft?: Maybe<Draft>;
  draftEdits: Array<DraftEdit>;
  draftGrid: DraftGrid;
  draftJob?: Maybe<DraftJob>;
  drafts: Array<Draft>;
  feed?: Maybe<Feed>;
  feedInfo?: Maybe<FeedInfo>;
  feeds: Array<Feed>;
  frequencies: Array<Frequency>;
  headway: Headway;
  levels: Array<Level>;
  pathways: Array<Pathway>;
  predictionAccuracy: Array<PredictionAccuracySummary>;
  records: Array<Scalars['JSON']['output']>;
  revision?: Maybe<Revision>;
  revisions: Array<Revision>;
  route?: Maybe<Route>;
  routes: Array<Route>;
  shape?: Maybe<Shape>;
  stop?: Maybe<Stop>;
  stopPredictions: Array<StopPrediction>;
  stopTimes: Array<StopTime>;
  stops: Array<Stop>;
  transfers: Array<Transfer>;
  trip?: Maybe<Trip>;
  tripPattern?: Maybe<TripPattern>;
  tripPatterns: Array<TripPattern>;
  trips: Array<Trip>;
  tripsOnDate: Array<Trip>;
  vehicle?: Maybe<Vehicle>;
  vehiclePredictions: Array<StopPrediction>;
  vehicles: Array<Vehicle>;
};


export type QueryAgenciesArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
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
  directionId?: InputMaybe<Scalars['Int']['input']>;
  feedCode: Scalars['String']['input'];
  routeId: Scalars['String']['input'];
  stopId: Scalars['String']['input'];
};


export type QueryLevelsArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryPathwaysArgs = {
  feedCode: Scalars['String']['input'];
  revisionId?: InputMaybe<Scalars['ID']['input']>;
};


export type QueryPredictionAccuracyArgs = {
  algorithm?: InputMaybe<Scalars['String']['input']>;
  feedCode: Scalars['String']['input'];
  sinceDays?: InputMaybe<Scalars['Int']['input']>;
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


export type QueryStopPredictionsArgs = {
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
  feedCode: Scalars['String']['input'];
  vehicleId: Scalars['String']['input'];
};


export type QueryVehiclesArgs = {
  feedCode: Scalars['String']['input'];
  matchedOnly?: InputMaybe<Scalars['Boolean']['input']>;
};

export type RegisterFeedInput = {
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
  stopIdOrder: Array<Scalars['String']['input']>;
  tripId: Scalars['String']['input'];
};

export type Revision = {
  __typename?: 'Revision';
  activatedAt?: Maybe<Scalars['String']['output']>;
  byteSize?: Maybe<Scalars['Int']['output']>;
  contentSha256?: Maybe<Scalars['String']['output']>;
  createdAt: Scalars['String']['output'];
  errorMessage?: Maybe<Scalars['String']['output']>;
  feedCode: Scalars['String']['output'];
  feedEndDate?: Maybe<Scalars['String']['output']>;
  feedStartDate?: Maybe<Scalars['String']['output']>;
  filesPresent: Array<Scalars['String']['output']>;
  id: Scalars['ID']['output'];
  rowCounts?: Maybe<Scalars['JSON']['output']>;
  status: RevisionStatus;
  supersededAt?: Maybe<Scalars['String']['output']>;
  validationSummary?: Maybe<ValidationSummary>;
};

export type RevisionStatus =
  | 'ACTIVE'
  | 'DERIVING'
  | 'DOWNLOADING'
  | 'FAILED'
  | 'PARSING'
  | 'PENDING'
  | 'READY'
  | 'SUPERSEDED'
  | 'UNCHANGED'
  | 'VALIDATING';

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
  tripPatterns: Array<TripPattern>;
  trips: Array<Trip>;
};

export type ScheduleTime = {
  __typename?: 'ScheduleTime';
  arrivalSec?: Maybe<Scalars['Int']['output']>;
  departureSec?: Maybe<Scalars['Int']['output']>;
  interpolated: Scalars['Boolean']['output'];
  schedDwellTimeSec?: Maybe<Scalars['Int']['output']>;
  schedTravelTimeSec?: Maybe<Scalars['Int']['output']>;
  stopPathIndex: Scalars['Int']['output'];
};

export type SetStopDwellInput = {
  draftId: Scalars['ID']['input'];
  dwellSec: Scalars['Int']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  stopSequence: Scalars['Int']['input'];
  tripId: Scalars['String']['input'];
};

export type Shape = {
  __typename?: 'Shape';
  lengthM?: Maybe<Scalars['Float']['output']>;
  pointCount: Scalars['Int']['output'];
  points: Array<ShapePoint>;
  shapeId: Scalars['String']['output'];
};

export type ShapePoint = {
  __typename?: 'ShapePoint';
  distTraveled?: Maybe<Scalars['Float']['output']>;
  lat?: Maybe<Scalars['Float']['output']>;
  lon?: Maybe<Scalars['Float']['output']>;
  sequence: Scalars['Int']['output'];
};

export type ShiftTripInput = {
  deltaSec: Scalars['Int']['input'];
  draftId: Scalars['ID']['input'];
  editor: Scalars['String']['input'];
  expectedVersion: Scalars['Long']['input'];
  tripId: Scalars['String']['input'];
};

export type Stop = {
  __typename?: 'Stop';
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

export type StopPath = {
  __typename?: 'StopPath';
  breakTimeSec?: Maybe<Scalars['Int']['output']>;
  dropOffType?: Maybe<Scalars['Int']['output']>;
  layoverStop: Scalars['Boolean']['output'];
  lengthM: Scalars['Float']['output'];
  pathGeometry?: Maybe<Scalars['JSON']['output']>;
  pickupType?: Maybe<Scalars['Int']['output']>;
  scheduleAdherenceStop: Scalars['Boolean']['output'];
  stop?: Maybe<Stop>;
  stopId: Scalars['String']['output'];
  stopPathIndex: Scalars['Int']['output'];
  stopSeq: Scalars['Int']['output'];
  typicalDwellTimeSec?: Maybe<Scalars['Int']['output']>;
  typicalTravelTimeSec?: Maybe<Scalars['Int']['output']>;
  waitStop: Scalars['Boolean']['output'];
};

export type StopPrediction = {
  __typename?: 'StopPrediction';
  actualArrival?: Maybe<Scalars['String']['output']>;
  actualDeparture?: Maybe<Scalars['String']['output']>;
  algorithm?: Maybe<Scalars['String']['output']>;
  confidenceSec?: Maybe<Scalars['Int']['output']>;
  predictedArrival?: Maybe<Scalars['String']['output']>;
  predictedDeparture?: Maybe<Scalars['String']['output']>;
  scheduledArrival?: Maybe<Scalars['String']['output']>;
  scheduledDeparture?: Maybe<Scalars['String']['output']>;
  stop?: Maybe<Stop>;
  stopPathIndex: Scalars['Int']['output'];
};

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

export type Trip = {
  __typename?: 'Trip';
  bikesAllowed?: Maybe<Scalars['Int']['output']>;
  block?: Maybe<Block>;
  blockId?: Maybe<Scalars['String']['output']>;
  directionId?: Maybe<Scalars['Int']['output']>;
  endTimeSec?: Maybe<Scalars['Int']['output']>;
  frequencyBased?: Maybe<Scalars['Boolean']['output']>;
  noSchedule?: Maybe<Scalars['Boolean']['output']>;
  pattern?: Maybe<TripPattern>;
  route?: Maybe<Route>;
  routeId: Scalars['String']['output'];
  scheduleTimes: Array<ScheduleTime>;
  serviceId: Scalars['String']['output'];
  shape?: Maybe<Shape>;
  shapeId?: Maybe<Scalars['String']['output']>;
  startTimeSec?: Maybe<Scalars['Int']['output']>;
  stopTimes: Array<StopTime>;
  tripHeadsign?: Maybe<Scalars['String']['output']>;
  tripId: Scalars['String']['output'];
  tripPatternId?: Maybe<Scalars['ID']['output']>;
  tripShortName?: Maybe<Scalars['String']['output']>;
  wheelchairAccessible?: Maybe<Scalars['Int']['output']>;
};

export type TripPattern = {
  __typename?: 'TripPattern';
  directionId?: Maybe<Scalars['Int']['output']>;
  extent?: Maybe<Extent>;
  headsign?: Maybe<Scalars['String']['output']>;
  lengthM?: Maybe<Scalars['Float']['output']>;
  patternKey: Scalars['String']['output'];
  route?: Maybe<Route>;
  routeId: Scalars['String']['output'];
  shapeId?: Maybe<Scalars['String']['output']>;
  stopCount: Scalars['Int']['output'];
  stopPaths: Array<StopPath>;
  tripCount: Scalars['Int']['output'];
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
  expectedVersion: Scalars['Long']['input'];
  stopSequence: Scalars['Int']['input'];
  tripId: Scalars['String']['input'];
};

export type ValidationSummary = {
  __typename?: 'ValidationSummary';
  errorCount: Scalars['Int']['output'];
  warningCount: Scalars['Int']['output'];
};

export type Vehicle = {
  __typename?: 'Vehicle';
  bearing?: Maybe<Scalars['Float']['output']>;
  block?: Maybe<Block>;
  currentStop?: Maybe<Stop>;
  distanceAlongTripM?: Maybe<Scalars['Float']['output']>;
  label?: Maybe<Scalars['String']['output']>;
  matched: Scalars['Boolean']['output'];
  occupancyStatus?: Maybe<Scalars['String']['output']>;
  pattern?: Maybe<TripPattern>;
  position: LatLon;
  reportTs: Scalars['String']['output'];
  scheduleAdherenceSec?: Maybe<Scalars['Int']['output']>;
  snappedPosition?: Maybe<LatLon>;
  speedMps?: Maybe<Scalars['Float']['output']>;
  stale: Scalars['Boolean']['output'];
  stopPathIndex?: Maybe<Scalars['Int']['output']>;
  trip?: Maybe<Trip>;
  vehicleId: Scalars['String']['output'];
};

export type AgenciesQueryVariables = Exact<{
  feedCode: string;
}>;


export type AgenciesQuery = { agencies: Array<{ agencyId: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AgenciesDetailQueryVariables = Exact<{
  feedCode: string;
}>;


export type AgenciesDetailQuery = { agencies: Array<{ agencyId: string | null, agencyName: string | null, agencyUrl: string | null, agencyTimezone: string | null, agencyPhone: string | null, agencyEmail: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AvlFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type AvlFeedsQuery = { avlFeeds: Array<{ code: string, name: string, gtfsFeedCode: string, enabled: boolean, lastPollAt: string | null, lastPollStatus: string | null, lastPollReportCount: number | null }> };

export type AvlTrailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  limit?: number | null | undefined;
}>;


export type AvlTrailQuery = { avlReports: Array<{ ts: string, speedMps: number | null, bearing: number | null, currentStatus: string | null, occupancyStatus: string | null, descTripId: string | null, position: { lat: number, lon: number } }> };

export type BlockDetailQueryVariables = Exact<{
  feedCode: string;
  blockId: string;
  serviceId: string;
}>;


export type BlockDetailQuery = { block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string>, blockTrips: Array<{ listIndex: number, layoverAfterSec: number | null, deadheadAfter: boolean | null, trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } }> } | null };

export type BlocksListQueryVariables = Exact<{
  feedCode: string;
}>;


export type BlocksListQuery = { blocks: Array<{ blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string> }> };

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


export type RebuildDraftMutation = { rebuildDraft: { id: string, state: string, phase: string, error: string | null } };

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


export type DraftsQuery = { drafts: Array<{ id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: string, kind: string, version: number, derivationStale: boolean, lastValidation: any, createdBy: string | null, createdAt: string, rowCounts: any, lock: { editor: string, expiresAt: string } | null }> };

export type DraftDetailQueryVariables = Exact<{
  id: string | number;
}>;


export type DraftDetailQuery = { draft: { id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: string, kind: string, version: number, derivationStale: boolean, lastValidation: any, createdBy: string | null, createdAt: string, rowCounts: any, lock: { editor: string, expiresAt: string } | null } | null };

export type DraftEditsQueryVariables = Exact<{
  id: string | number;
  limit?: number | null | undefined;
}>;


export type DraftEditsQuery = { draftEdits: Array<{ seq: number, op: string, summary: string, appliedAt: string, undone: boolean }> };

export type DraftJobQueryVariables = Exact<{
  jobId: string | number;
}>;


export type DraftJobQuery = { draftJob: { id: string, state: string, phase: string, error: string | null } | null };

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


export type FeedDetailQuery = { feed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean, source: string, activeRevision: { id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, filesPresent: Array<string>, rowCounts: any, validationSummary: { errorCount: number, warningCount: number } | null } | null, revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, supersededAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, errorMessage: string | null, validationSummary: { errorCount: number, warningCount: number } | null }> } | null, feedInfo: { feedPublisherName: string | null, feedPublisherUrl: string | null, feedLang: string | null, feedStartDate: string | null, feedEndDate: string | null, feedVersion: string | null, feedContactEmail: string | null, feedContactUrl: string | null } | null };

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

export type FeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type FeedsQuery = { feeds: Array<{ code: string, name: string, description: string | null, enabled: boolean, source: string, activeRevision: { id: string, status: RevisionStatus, feedStartDate: string | null, feedEndDate: string | null } | null }> };

export type HeadwayInfoQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  routeId: string;
  directionId?: number | null | undefined;
}>;


export type HeadwayInfoQuery = { headway: { stopId: string, routeId: string, directionId: number | null, waitSec: number | null, gapsSec: Array<number>, scheduledHeadwaySec: number | null } };

export type HeadwayRoutePatternsQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;


export type HeadwayRoutePatternsQuery = { tripPatterns: Array<{ patternKey: string, directionId: number | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, stop: { stopName: string | null } | null }> }> };

export type PredictionAccuracyQueryVariables = Exact<{
  feedCode: string;
  sinceDays?: number | null | undefined;
}>;


export type PredictionAccuracyQuery = { predictionAccuracy: Array<{ algorithm: string, sampleCount: number, meanErrorSec: number, meanAbsErrorSec: number }> };

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
}>;


export type StopBoardQuery = { stopPredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: string | null, confidenceSec: number | null }> };

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


export type VehicleDetailQuery = { vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: string | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null, block: { blockId: string, blockTrips: Array<{ listIndex: number, trip: { tripId: string, tripHeadsign: string | null } }> } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { points: Array<{ lat: number | null, lon: number | null }> } | null } | null, pattern: { patternKey: string, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, pathGeometry: any, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null, vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: string | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>, predictionAccuracy: Array<{ algorithm: string, sampleCount: number, meanErrorSec: number, meanAbsErrorSec: number }> };

export type VehiclesQueryVariables = Exact<{
  feedCode: string;
  matchedOnly?: boolean | null | undefined;
}>;


export type VehiclesQuery = { vehicles: Array<{ vehicleId: string, label: string | null, reportTs: string, bearing: number | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, speedMps: number | null, position: { lat: number, lon: number }, trip: { routeId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } | null }> };

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
    enabled
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
export const AvlTrailDocument = gql`
    query AvlTrail($feedCode: String!, $vehicleId: String!, $limit: Int) {
  avlReports(feedCode: $feedCode, vehicleId: $vehicleId, limit: $limit) {
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
    query HeadwayInfo($feedCode: String!, $stopId: String!, $routeId: String!, $directionId: Int) {
  headway(
    feedCode: $feedCode
    stopId: $stopId
    routeId: $routeId
    directionId: $directionId
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
    query StopBoard($feedCode: String!, $stopId: String!, $routeId: String, $directionId: Int) {
  stopPredictions(
    feedCode: $feedCode
    stopId: $stopId
    routeId: $routeId
    directionId: $directionId
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
    predictedArrival
    actualArrival
    algorithm
    confidenceSec
  }
  predictionAccuracy(feedCode: $feedCode) {
    algorithm
    sampleCount
    meanErrorSec
    meanAbsErrorSec
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
    speedMps
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