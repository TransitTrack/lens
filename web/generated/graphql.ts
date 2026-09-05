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

export enum RevisionStatus {
  Active = 'ACTIVE',
  Deriving = 'DERIVING',
  Downloading = 'DOWNLOADING',
  Failed = 'FAILED',
  Parsing = 'PARSING',
  Pending = 'PENDING',
  Ready = 'READY',
  Superseded = 'SUPERSEDED',
  Unchanged = 'UNCHANGED',
  Validating = 'VALIDATING'
}

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

export enum Table {
  Areas = 'AREAS',
  Attributions = 'ATTRIBUTIONS',
  BookingRules = 'BOOKING_RULES',
  FareAttributes = 'FARE_ATTRIBUTES',
  FareLegJoinRules = 'FARE_LEG_JOIN_RULES',
  FareLegRules = 'FARE_LEG_RULES',
  FareMedia = 'FARE_MEDIA',
  FareProducts = 'FARE_PRODUCTS',
  FareRules = 'FARE_RULES',
  FareTransferRules = 'FARE_TRANSFER_RULES',
  Locations = 'LOCATIONS',
  LocationGroups = 'LOCATION_GROUPS',
  LocationGroupStops = 'LOCATION_GROUP_STOPS',
  Networks = 'NETWORKS',
  RiderCategories = 'RIDER_CATEGORIES',
  RouteNetworks = 'ROUTE_NETWORKS',
  StopAreas = 'STOP_AREAS',
  Timeframes = 'TIMEFRAMES',
  Translations = 'TRANSLATIONS'
}

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

export type AvlFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type AvlFeedsQuery = { avlFeeds: Array<{ code: string, name: string, gtfsFeedCode: string, enabled: boolean, lastPollAt: string | null, lastPollStatus: string | null }> };

export type VehiclePredictionsQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
}>;


export type VehiclePredictionsQuery = { vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: string | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }> };

export type VehiclesQueryVariables = Exact<{
  feedCode: string;
  matchedOnly?: boolean | null | undefined;
}>;


export type VehiclesQuery = { vehicles: Array<{ vehicleId: string, label: string | null, reportTs: string, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, speedMps: number | null, position: { lat: number, lon: number } }> };


export const AvlFeedsDocument = gql`
    query AvlFeeds {
  avlFeeds {
    code
    name
    gtfsFeedCode
    enabled
    lastPollAt
    lastPollStatus
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
export const VehiclePredictionsDocument = gql`
    query VehiclePredictions($feedCode: String!, $vehicleId: String!) {
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
}
    `;

/**
 * __useVehiclePredictionsQuery__
 *
 * To run a query within a Vue component, call `useVehiclePredictionsQuery` and pass it any options that fit your needs.
 * When your component renders, `useVehiclePredictionsQuery` returns an object from Apollo Client that contains result, loading and error properties
 * you can use to render your UI.
 *
 * @param variables that will be passed into the query
 * @param options that will be passed into the query, supported options are listed on: https://v4.apollo.vuejs.org/guide-composable/query.html#options;
 *
 * @example
 * const { result, loading, error } = useVehiclePredictionsQuery({
 *   feedCode: // value for 'feedCode'
 *   vehicleId: // value for 'vehicleId'
 * });
 */
export function useVehiclePredictionsQuery(variables: VehiclePredictionsQueryVariables | VueCompositionApi.Ref<VehiclePredictionsQueryVariables> | ReactiveFunction<VehiclePredictionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>> = {}) {
  return VueApolloComposable.useQuery<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>(VehiclePredictionsDocument, variables, options);
}
export function useVehiclePredictionsLazyQuery(variables?: VehiclePredictionsQueryVariables | VueCompositionApi.Ref<VehiclePredictionsQueryVariables> | ReactiveFunction<VehiclePredictionsQueryVariables>, options: VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables> | VueCompositionApi.Ref<VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>> | ReactiveFunction<VueApolloComposable.UseQueryOptions<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>> = {}) {
  return VueApolloComposable.useLazyQuery<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>(VehiclePredictionsDocument, variables, options);
}
export type VehiclePredictionsQueryCompositionFunctionResult = VueApolloComposable.UseQueryReturn<VehiclePredictionsQuery, VehiclePredictionsQueryVariables>;
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
    matched
    stale
    scheduleAdherenceSec
    stopPathIndex
    speedMps
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