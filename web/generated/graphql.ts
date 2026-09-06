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
  activateRevision: Revision;
  deleteFeed: Scalars['Boolean']['output'];
  deleteRevision: Scalars['Boolean']['output'];
  ingestFeed: Revision;
  registerFeed: Feed;
  updateFeed: Feed;
};


export type MutationActivateRevisionArgs = {
  revisionId: Scalars['ID']['input'];
};


export type MutationDeleteFeedArgs = {
  code: Scalars['String']['input'];
};


export type MutationDeleteRevisionArgs = {
  revisionId: Scalars['ID']['input'];
};


export type MutationIngestFeedArgs = {
  feedCode: Scalars['String']['input'];
};


export type MutationRegisterFeedArgs = {
  input: RegisterFeedInput;
};


export type MutationUpdateFeedArgs = {
  code: Scalars['String']['input'];
  input: UpdateFeedInput;
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


export type AvlTrailQuery = { avlReports: Array<{ ts: string, speedMps: number | null, bearing: number | null, position: { lat: number, lon: number } }> };

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


export type FeedDetailQuery = { feed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean, source: string, activeRevision: { id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, filesPresent: Array<string>, rowCounts: any, validationSummary: { errorCount: number, warningCount: number } | null } | null, revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, supersededAt: string | null, byteSize: number | null, errorMessage: string | null, validationSummary: { errorCount: number, warningCount: number } | null }> } | null, feedInfo: { feedPublisherName: string | null, feedPublisherUrl: string | null, feedLang: string | null, feedStartDate: string | null, feedEndDate: string | null, feedVersion: string | null, feedContactEmail: string | null, feedContactUrl: string | null } | null };

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


export type TripsByRouteQuery = { trips: Array<{ tripId: string, tripHeadsign: string | null, tripShortName: string | null, directionId: number | null, serviceId: string, shapeId: string | null, blockId: string | null }> };

export type VehicleDetailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
}>;


export type VehicleDetailQuery = { vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: string | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { points: Array<{ lat: number | null, lon: number | null }> } | null } | null, pattern: { patternKey: string, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, pathGeometry: any, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null, vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: string | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>, predictionAccuracy: Array<{ algorithm: string, sampleCount: number, meanErrorSec: number, meanAbsErrorSec: number }> };

export type VehiclesQueryVariables = Exact<{
  feedCode: string;
  matchedOnly?: boolean | null | undefined;
}>;


export type VehiclesQuery = { vehicles: Array<{ vehicleId: string, label: string | null, reportTs: string, bearing: number | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, speedMps: number | null, position: { lat: number, lon: number }, trip: { routeId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } | null }> };


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
      byteSize
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
