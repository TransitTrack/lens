/* tslint:disable */
/* eslint-disable */
/**
 * 
 * @export
 * @interface ActivateDraftRequest
 */
export interface ActivateDraftRequest {
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    force?: boolean;
}
/**
 * 
 * @export
 * @interface AddTripInput
 */
export interface AddTripInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    serviceId: string;
    /**
     * Explicit trip ID, or omit to have one generated.
     */
    tripId?: string | null;
    /**
     * 
     */
    headsign?: string | null;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * 
     */
    shapeId?: string | null;
    /**
     * 
     */
    blockId?: string | null;
    /**
     * The new trip's scheduled stops, in order.
     */
    stops: Array<NewStopTimeInput>;
}
/**
 * GTFS agency.txt: a transit operator.
 * @export
 * @interface Agency
 */
export interface Agency {
    /**
     * 
     */
    agencyId?: string | null;
    /**
     * 
     */
    agencyName?: string | null;
    /**
     * 
     */
    agencyUrl?: string | null;
    /**
     * 
     */
    agencyTimezone?: string | null;
    /**
     * 
     */
    agencyLang?: string | null;
    /**
     * 
     */
    agencyPhone?: string | null;
    /**
     * 
     */
    agencyFareUrl?: string | null;
    /**
     * 
     */
    agencyEmail?: string | null;
    /**
     * 
     */
    extent?: Extent | null;
}
/**
 * 
 * @export
 * @interface ApplyOptimizationRecommendationsRequest
 */
export interface ApplyOptimizationRecommendationsRequest {
    /**
     * 
     */
    recommendationIds: Array<string>;
    /**
     * 
     */
    label?: string;
    /**
     * 
     */
    editor: string;
}

/**
 * 
 * @export
 */
export const AvlAssignmentMode = {
    TrustDescriptor: 'TRUST_DESCRIPTOR',
    DescriptorThenInfer: 'DESCRIPTOR_THEN_INFER',
    FullInference: 'FULL_INFERENCE',
} as const;
export type AvlAssignmentMode = typeof AvlAssignmentMode[keyof typeof AvlAssignmentMode];

/**
 * A configured AVL (vehicle-position) feed: where it's polled from, how its reports are decoded, and how vehicles on it are matched to trips.
 * @export
 * @interface AvlFeed
 */
export interface AvlFeed {
    /**
     * Unique identifier for this AVL feed, independent of the GTFS feed it matches against.
     */
    code: string;
    /**
     * 
     */
    name: string;
    /**
     * The gtfs.graphqls Feed.code whose active revision this AVL feed's vehicles are matched against.
     */
    gtfsFeedCode: string;
    /**
     * 
     */
    format: AvlFormat;
    /**
     * How often this feed is polled, in seconds.
     */
    pollIntervalSec: number;
    /**
     * 
     */
    assignmentMode: AvlAssignmentMode;
    /**
     * Prediction algorithms run for vehicles on this feed; the first is used as the default when a query doesn't specify one.
     */
    predictionAlgorithms: Array<PredictionAlgorithm>;
    /**
     * 
     */
    enabled: boolean;
    /**
     * ISO-8601 instant of the most recent poll attempt, or null if this feed has never been polled.
     */
    lastPollAt?: string | null;
    /**
     * Outcome of the most recent poll attempt (e.g. success or an error description).
     */
    lastPollStatus?: string | null;
    /**
     * Number of AVL reports ingested on the most recent poll.
     */
    lastPollReportCount?: number | null;
}



/**
 * 
 * @export
 */
export const AvlFormat = {
    GtfsRt: 'GTFS_RT',
    Stpt: 'STPT',
} as const;
export type AvlFormat = typeof AvlFormat[keyof typeof AvlFormat];


/**
 * 
 * @export
 */
export const AvlOccupancyStatus = {
    Empty: 'EMPTY',
    ManySeatsAvailable: 'MANY_SEATS_AVAILABLE',
    FewSeatsAvailable: 'FEW_SEATS_AVAILABLE',
    StandingRoomOnly: 'STANDING_ROOM_ONLY',
    CrushedStandingRoomOnly: 'CRUSHED_STANDING_ROOM_ONLY',
    Full: 'FULL',
    NotAcceptingPassengers: 'NOT_ACCEPTING_PASSENGERS',
    NoDataAvailable: 'NO_DATA_AVAILABLE',
    NotBoardable: 'NOT_BOARDABLE',
} as const;
export type AvlOccupancyStatus = typeof AvlOccupancyStatus[keyof typeof AvlOccupancyStatus];

/**
 * One raw AVL (vehicle-position) report as received from the feed, plus the outcome of matching it to a trip.
 * @export
 * @interface AvlReport
 */
export interface AvlReport {
    /**
     * 
     */
    vehicleId: string;
    /**
     * ISO-8601 instant the vehicle itself reported this position.
     */
    ts: string;
    /**
     * ISO-8601 instant this report was ingested by transittrack.
     */
    processedAt: string;
    /**
     * The AVL feed protocol this report was decoded from.
     */
    source: AvlFormat;
    /**
     * 
     */
    position: LatLon;
    /**
     * 
     */
    bearing?: number | null;
    /**
     * 
     */
    speedMps?: number | null;
    /**
     * 
     */
    currentStatus?: VehicleStopStatus | null;
    /**
     * 
     */
    occupancyStatus?: AvlOccupancyStatus | null;
    /**
     * The trip ID the feed itself reported for this vehicle (its raw descriptor), before any transittrack matching - null if the feed didn't supply one.
     */
    descTripId?: string | null;
    /**
     * 
     */
    matchStatus: MatchStatus;
    /**
     * The resolved trip match, present only when matchStatus is MATCHED.
     */
    match?: AvlReportMatch | null;
}


/**
 * 
 * @export
 * @interface AvlReportCatalog
 */
export interface AvlReportCatalog {
    /**
     * IANA time zone the feed's agency operates in, used to interpret avlReportPage's date/startTime/endTime.
     */
    timeZone: string;
    /**
     * Every distinct vehicle ID this feed has ever reported.
     */
    vehicleIds: Array<string>;
}
/**
 * The resolved trip match for an AvlReport whose matchStatus is MATCHED.
 * @export
 * @interface AvlReportMatch
 */
export interface AvlReportMatch {
    /**
     * Index into the matched trip pattern's stopPaths this report's position falls within.
     */
    stopPathIndex: number;
    /**
     * Distance in meters along the matched trip's shape.
     */
    distanceAlongTripM: number;
    /**
     * Perpendicular distance in meters between the raw reported position and the matched shape.
     */
    deviationM: number;
    /**
     * Seconds behind (positive) or ahead of (negative) schedule at this position.
     */
    scheduleAdherenceSec?: number | null;
    /**
     * Heading in degrees inferred from the match, independent of any bearing the feed itself reported.
     */
    heading?: number | null;
    /**
     * Match confidence score; higher is a stronger match. Not comparable across matcher implementations.
     */
    score?: number | null;
    /**
     * The raw position snapped onto the matched trip's shape.
     */
    snappedPosition: LatLon;
    /**
     * GTFS revision the report was matched against.
     */
    revisionId: string;
    /**
     * GTFS trip_id of the matched trip.
     */
    tripId: string;
}
/**
 * A page of AvlReport rows for one vehicle within a requested time window.
 * @export
 * @interface AvlReportPage
 */
export interface AvlReportPage {
    /**
     * 
     */
    items: Array<AvlReport>;
    /**
     * 
     */
    totalCount: number;
    /**
     * 
     */
    page: number;
    /**
     * 
     */
    pageSize: number;
}
/**
 * A vehicle block: the ordered sequence of trips one vehicle is scheduled to run on a given service day, derived from shared GTFS block_id.
 * @export
 * @interface Block
 */
export interface Block {
    /**
     * Internal row ID, distinct from blockId (which may repeat across services).
     */
    id: string;
    /**
     * 
     */
    blockId: string;
    /**
     * 
     */
    serviceId: string;
    /**
     * Scheduled start of the block's first trip, in seconds since midnight.
     */
    startTimeSec: number;
    /**
     * Scheduled end of the block's last trip, in seconds since midnight.
     */
    endTimeSec: number;
    /**
     * 
     */
    tripCount: number;
    /**
     * Every route served by a trip in this block.
     */
    routeIds: Array<string>;
}
/**
 * One trip's position within its block's scheduled sequence.
 * @export
 * @interface BlockTrip
 */
export interface BlockTrip {
    /**
     * Position of this trip within the block, starting at 0.
     */
    listIndex: number;
    /**
     * Scheduled layover in seconds between this trip ending and the next one starting, or null for the block's last trip.
     */
    layoverAfterSec?: number | null;
    /**
     * True if the vehicle must deadhead (travel out of service) to reach the next trip's start, or null if not determined.
     */
    deadheadAfter?: boolean | null;
    /**
     * GTFS trip_id of this trip.
     */
    tripId: string;
    /**
     * blockId of the block this trip belongs to; with serviceId it identifies the block.
     */
    blockId: string;
    /**
     * 
     */
    serviceId: string;
}
/**
 * 
 * @export
 * @interface BulkShiftTripsInput
 */
export interface BulkShiftTripsInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * Restrict to trips on this route; omit to match across routes.
     */
    routeId?: string | null;
    /**
     * Restrict to trips of this derived trip pattern.
     */
    patternKey?: string | null;
    /**
     * 
     */
    serviceId?: string | null;
    /**
     * Restrict to trips starting at or after this time, in seconds since midnight.
     */
    windowFromSec?: number | null;
    /**
     * Restrict to trips starting at or before this time, in seconds since midnight.
     */
    windowToSec?: number | null;
    /**
     * Signed offset in seconds applied to every matching trip's stop times.
     */
    deltaSec: number;
}
/**
 * GTFS calendar.txt: a named service pattern's days of the week and validity range.
 * @export
 * @interface Calendar
 */
export interface Calendar {
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    monday?: boolean | null;
    /**
     * 
     */
    tuesday?: boolean | null;
    /**
     * 
     */
    wednesday?: boolean | null;
    /**
     * 
     */
    thursday?: boolean | null;
    /**
     * 
     */
    friday?: boolean | null;
    /**
     * 
     */
    saturday?: boolean | null;
    /**
     * 
     */
    sunday?: boolean | null;
    /**
     * 
     */
    startDate?: string | null;
    /**
     * 
     */
    endDate?: string | null;
}
/**
 * One calendar's change between the two compared revisions.
 * @export
 * @interface CalendarChange
 */
export interface CalendarChange {
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    kind: CalendarChangeKind;
    /**
     * Changed top-level calendar fields (day-of-week flags, startDate, endDate), keyed by field name to {from, to} old/new value pairs.
     */
    fieldChanges: any | null;
    /**
     * Calendar date exceptions added, removed, or changed for this service.
     */
    exceptionChanges: Array<CalendarExceptionChange>;
}



/**
 * 
 * @export
 */
export const CalendarChangeKind = {
    Added: 'ADDED',
    Removed: 'REMOVED',
    Modified: 'MODIFIED',
} as const;
export type CalendarChangeKind = typeof CalendarChangeKind[keyof typeof CalendarChangeKind];

/**
 * GTFS calendar_dates.txt: a single-day exception to a service's calendar. exceptionType: 1=service added, 2=service removed.
 * @export
 * @interface CalendarDate
 */
export interface CalendarDate {
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    date: string;
    /**
     * 
     */
    exceptionType?: number | null;
}
/**
 * One calendar date exception's change within a changed calendar.
 * @export
 * @interface CalendarExceptionChange
 */
export interface CalendarExceptionChange {
    /**
     * 
     */
    date: string;
    /**
     * 
     */
    kind: ExceptionChangeKind;
    /**
     * The exception_type (1=added, 2=removed) before this change, or null if it didn't previously exist.
     */
    fromType?: number | null;
    /**
     * The exception_type after this change, or null if it was removed.
     */
    toType?: number | null;
}


/**
 * 
 * @export
 * @interface ClaimDraftEditorRequest
 */
export interface ClaimDraftEditorRequest {
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    takeOver?: boolean;
}
/**
 * 
 * @export
 * @interface DeleteTripInput
 */
export interface DeleteTripInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
}
/**
 * 
 * @export
 * @interface DiscardDraftRequest
 */
export interface DiscardDraftRequest {
    /**
     * 
     */
    editor: string;
}
/**
 * A revision being edited: a fork of a base revision, plus its edit lock and validation state.
 * @export
 * @interface Draft
 */
export interface Draft {
    /**
     * 
     */
    id: string;
    /**
     * 
     */
    feedCode: string;
    /**
     * Operator-facing name for this draft, or null if unset.
     */
    label?: string | null;
    /**
     * The revision this draft was forked from, or null if it predates fork tracking.
     */
    baseRevisionId?: string | null;
    /**
     * 
     */
    status: RevisionStatus;
    /**
     * 
     */
    kind: DraftKind;
    /**
     * Optimistic-concurrency version, incremented by every edit mutation and required (as expectedVersion) by mutations and undo/redo to detect concurrent edits.
     */
    version: number;
    /**
     * True if the draft has been edited since its last rebuildDraft, so its derived schedule data (and validation) may not reflect the latest edits.
     */
    derivationStale: boolean;
    /**
     * Validation results from the draft's last rebuildDraft, or null if it hasn't been rebuilt.
     */
    lastValidation?: any | null;
    /**
     * The current edit lock, or null if no editor currently holds it.
     */
    lock?: DraftLock | null;
    /**
     * The editor who created this draft, or null if unknown.
     */
    createdBy?: string | null;
    /**
     * 
     */
    createdAt: string;
    /**
     * Row counts per GTFS table, as of the draft's data.
     */
    rowCounts?: any | null;
}


/**
 * One entry in a draft's edit history.
 * @export
 * @interface DraftEdit
 */
export interface DraftEdit {
    /**
     * Sequence number within the draft's edit history, increasing with each new edit.
     */
    seq: number;
    /**
     * Upper-snake-case code identifying the kind of edit (e.g. UPDATE_STOP_TIME, SHIFT_TRIP, ADD_TRIP).
     */
    op: string;
    /**
     * Human-readable summary of what this edit changed.
     */
    summary: string;
    /**
     * 
     */
    appliedAt: string;
    /**
     * True if this edit has since been reverted via undoDraftEdit.
     */
    undone: boolean;
}
/**
 * The result of an edit mutation: the draft's new state, the edit record it produced, and whether undo/redo are now available.
 * @export
 * @interface DraftEditResult
 */
export interface DraftEditResult {
    /**
     * 
     */
    draft: Draft;
    /**
     * The edit this mutation recorded, or null for a mutation that doesn't produce an undoable edit.
     */
    edit?: DraftEdit | null;
    /**
     * 
     */
    canUndo: boolean;
    /**
     * 
     */
    canRedo: boolean;
}
/**
 * A draft's trips and stops laid out as an editable grid, scoped to one route/direction/service.
 * @export
 * @interface DraftGrid
 */
export interface DraftGrid {
    /**
     * The scheduled stops shared by every trip in the grid, in stop_sequence order.
     */
    stops: Array<DraftGridStop>;
    /**
     * 
     */
    trips: Array<DraftGridTrip>;
}
/**
 * 
 * @export
 * @interface DraftGridCell
 */
export interface DraftGridCell {
    /**
     * 
     */
    stopSequence: number;
    /**
     * Scheduled arrival in seconds since midnight, or null if this trip doesn't serve this stop.
     */
    arrivalSec?: number | null;
    /**
     * 
     */
    departureSec?: number | null;
}
/**
 * 
 * @export
 * @interface DraftGridStop
 */
export interface DraftGridStop {
    /**
     * 
     */
    stopSequence: number;
    /**
     * 
     */
    stopId?: string | null;
    /**
     * 
     */
    stopName?: string | null;
    /**
     * GTFS timepoint for this stop (0=approximate, 1=exact), or null if not scheduled.
     */
    timepoint?: number | null;
}
/**
 * One trip's row in a draft grid, with one cell per stop in the shared stops list.
 * @export
 * @interface DraftGridTrip
 */
export interface DraftGridTrip {
    /**
     * 
     */
    tripId: string;
    /**
     * Seconds since midnight of the trip's first scheduled departure, for sorting rows.
     */
    firstDepartureSec?: number | null;
    /**
     * 
     */
    headsign?: string | null;
    /**
     * 
     */
    blockId?: string | null;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * 
     */
    serviceId?: string | null;
    /**
     * Cells aligned to the grid's stops list, by stopSequence.
     */
    cells: Array<DraftGridCell>;
}
/**
 * Status of an asynchronous rebuildDraft job, pollable via the draftJob query.
 * @export
 * @interface DraftJob
 */
export interface DraftJob {
    /**
     * 
     */
    id: string;
    /**
     * 
     */
    state: DraftJobState;
    /**
     * 
     */
    phase: DraftJobPhase;
    /**
     * Failure detail when state is FAILED, or null otherwise.
     */
    error?: string | null;
}



/**
 * 
 * @export
 */
export const DraftJobPhase = {
    Deriving: 'DERIVING',
    Validating: 'VALIDATING',
    Done: 'DONE',
} as const;
export type DraftJobPhase = typeof DraftJobPhase[keyof typeof DraftJobPhase];


/**
 * 
 * @export
 */
export const DraftJobState = {
    Running: 'RUNNING',
    Succeeded: 'SUCCEEDED',
    Failed: 'FAILED',
} as const;
export type DraftJobState = typeof DraftJobState[keyof typeof DraftJobState];


/**
 * 
 * @export
 */
export const DraftKind = {
    Import: 'IMPORT',
    Draft: 'DRAFT',
} as const;
export type DraftKind = typeof DraftKind[keyof typeof DraftKind];

/**
 * The exclusive edit lock currently held on a draft.
 * @export
 * @interface DraftLock
 */
export interface DraftLock {
    /**
     * 
     */
    editor: string;
    /**
     * ISO-8601 instant the lock expires unless renewed via renewDraftEditor.
     */
    expiresAt: string;
}
/**
 * 
 * @export
 * @interface DuplicateTripInput
 */
export interface DuplicateTripInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    sourceTripId: string;
    /**
     * Explicit trip ID for the copy, or omit to have one generated.
     */
    newTripId?: string | null;
    /**
     * Signed offset in seconds applied to every stop time of the copy, relative to sourceTripId.
     */
    offsetSec: number;
}
/**
 * How many analyzer findings one entity has, and the most severe of them.
 * @export
 * @interface EntityFindingCount
 */
export interface EntityFindingCount {
    /**
     * 
     */
    entityId: string;
    /**
     * 
     */
    count: number;
    /**
     * 
     */
    worstSeverity: FindingSeverity;
}



/**
 * 
 * @export
 */
export const ExceptionChangeKind = {
    ExceptionAdded: 'EXCEPTION_ADDED',
    ExceptionRemoved: 'EXCEPTION_REMOVED',
    ExceptionChanged: 'EXCEPTION_CHANGED',
} as const;
export type ExceptionChangeKind = typeof ExceptionChangeKind[keyof typeof ExceptionChangeKind];

/**
 * Geographic bounding box, in decimal degrees.
 * @export
 * @interface Extent
 */
export interface Extent {
    /**
     * 
     */
    minLat: number;
    /**
     * 
     */
    minLon: number;
    /**
     * 
     */
    maxLat: number;
    /**
     * 
     */
    maxLon: number;
}
/**
 * A configured GTFS static feed: where its data comes from, and its ingest history.
 * @export
 * @interface Feed
 */
export interface Feed {
    /**
     * Unique identifier for this feed, referenced by AvlFeed.gtfsFeedCode and throughout the GTFS query API.
     */
    code: string;
    /**
     * 
     */
    name: string;
    /**
     * 
     */
    description?: string | null;
    /**
     * URL the feed's GTFS zip is downloaded from on ingest.
     */
    url: string;
    /**
     * Cron expression controlling how often this feed is automatically re-ingested, or null if it's only ingested on demand.
     */
    pollingCron?: string | null;
    /**
     * 
     */
    enabled: boolean;
    /**
     * 
     */
    source: FeedSource;
}


/**
 * 
 * @export
 * @interface FeedGeometry200Response
 */
export interface FeedGeometry200Response {
    /**
     * 
     */
    type: FeedGeometry200ResponseTypeEnum;
    /**
     * 
     */
    features: Array<FeedGeometry200ResponseFeaturesInner>;
}


/**
 * @export
 */
export const FeedGeometry200ResponseTypeEnum = {
    FeatureCollection: 'FeatureCollection',
} as const;
export type FeedGeometry200ResponseTypeEnum = typeof FeedGeometry200ResponseTypeEnum[keyof typeof FeedGeometry200ResponseTypeEnum];

/**
 * 
 * @export
 * @interface FeedGeometry200ResponseFeaturesInner
 */
export interface FeedGeometry200ResponseFeaturesInner {
    /**
     * 
     */
    type: FeedGeometry200ResponseFeaturesInnerTypeEnum;
    /**
     * 
     */
    geometry: FeedGeometry200ResponseFeaturesInnerGeometry;
    /**
     * 
     */
    properties: { [key: string]: any | null; };
}


/**
 * @export
 */
export const FeedGeometry200ResponseFeaturesInnerTypeEnum = {
    Feature: 'Feature',
} as const;
export type FeedGeometry200ResponseFeaturesInnerTypeEnum = typeof FeedGeometry200ResponseFeaturesInnerTypeEnum[keyof typeof FeedGeometry200ResponseFeaturesInnerTypeEnum];

/**
 * @type FeedGeometry200ResponseFeaturesInnerGeometry
 * 
 * @export
 */
export type FeedGeometry200ResponseFeaturesInnerGeometry = FeedGeometry200ResponseFeaturesInnerGeometryOneOf | FeedGeometry200ResponseFeaturesInnerGeometryOneOf1;
/**
 * 
 * @export
 * @interface FeedGeometry200ResponseFeaturesInnerGeometryOneOf
 */
export interface FeedGeometry200ResponseFeaturesInnerGeometryOneOf {
    /**
     * 
     */
    type: any | null;
    /**
     * 
     */
    coordinates: Array<number>;
}
/**
 * 
 * @export
 * @interface FeedGeometry200ResponseFeaturesInnerGeometryOneOf1
 */
export interface FeedGeometry200ResponseFeaturesInnerGeometryOneOf1 {
    /**
     * 
     */
    type: any | null;
    /**
     * 
     */
    coordinates: Array<Array<number>>;
}
/**
 * GTFS feed_info.txt: feed-level publisher metadata.
 * @export
 * @interface FeedInfo
 */
export interface FeedInfo {
    /**
     * 
     */
    feedPublisherName?: string | null;
    /**
     * 
     */
    feedPublisherUrl?: string | null;
    /**
     * 
     */
    feedLang?: string | null;
    /**
     * 
     */
    defaultLang?: string | null;
    /**
     * 
     */
    feedStartDate?: string | null;
    /**
     * 
     */
    feedEndDate?: string | null;
    /**
     * 
     */
    feedVersion?: string | null;
    /**
     * 
     */
    feedContactEmail?: string | null;
    /**
     * 
     */
    feedContactUrl?: string | null;
}

/**
 * 
 * @export
 */
export const FeedSource = {
    Config: 'CONFIG',
    Api: 'API',
} as const;
export type FeedSource = typeof FeedSource[keyof typeof FeedSource];


/**
 * 
 * @export
 */
export const FindingSeverity = {
    Critical: 'CRITICAL',
    High: 'HIGH',
    Medium: 'MEDIUM',
    Low: 'LOW',
    Info: 'INFO',
} as const;
export type FindingSeverity = typeof FindingSeverity[keyof typeof FindingSeverity];

/**
 * 
 * @export
 * @interface ForkDraftInput
 */
export interface ForkDraftInput {
    /**
     * 
     */
    feedCode: string;
    /**
     * Revision to fork from, or omit to fork the feed's current active revision.
     */
    baseRevisionId?: string | null;
    /**
     * 
     */
    label?: string | null;
    /**
     * 
     */
    editor: string;
}
/**
 * GTFS frequencies.txt: a headway-based (rather than fixed-time) scheduling window for a trip. exactTimes: 0=frequency-based (approximate), 1=schedule-based (exact).
 * @export
 * @interface Frequency
 */
export interface Frequency {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    startTime: string;
    /**
     * 
     */
    endTime?: string | null;
    /**
     * 
     */
    headwaySecs?: number | null;
    /**
     * 
     */
    exactTimes?: number | null;
}
/**
 * Current gap between consecutive vehicles serving a route/direction at a stop, compared to what the schedule calls for.
 * @export
 * @interface Headway
 */
export interface Headway {
    /**
     * 
     */
    stopId: string;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * Seconds until the next vehicle is predicted to reach this stop, or null if none is currently predicted.
     */
    waitSec?: number | null;
    /**
     * Observed gaps in seconds between the vehicles currently predicted at this stop, in arrival order.
     */
    gapsSec: Array<number>;
    /**
     * The schedule's own average headway at this stop, in seconds, for comparison: trips running that service day, from an hour before the first predicted arrival to an hour after the last (around now if none). Frequency-based trips are left out.
     */
    scheduledHeadwaySec?: number | null;
}
/**
 * Scheduled headway comparison for one route/direction/service between the two compared revisions.
 * @export
 * @interface HeadwaySummary
 */
export interface HeadwaySummary {
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    fromTripCount: number;
    /**
     * 
     */
    toTripCount: number;
    /**
     * Mean gap between consecutive trips in fromRevisionId, in seconds.
     */
    fromMeanGapSec?: number | null;
    /**
     * 
     */
    toMeanGapSec?: number | null;
    /**
     * Largest gap between consecutive trips in fromRevisionId, in seconds.
     */
    fromMaxGapSec?: number | null;
    /**
     * 
     */
    toMaxGapSec?: number | null;
}
/**
 * 
 * @export
 * @interface InsertTripStopInput
 */
export interface InsertTripStopInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * The existing stopSequence the new stop is inserted after.
     */
    afterStopSequence: number;
    /**
     * 
     */
    stopId: string;
    /**
     * 
     */
    arrivalSec?: number | null;
    /**
     * 
     */
    departureSec?: number | null;
}
/**
 * 
 * @export
 * @interface LatLon
 */
export interface LatLon {
    /**
     * 
     */
    lat: number;
    /**
     * 
     */
    lon: number;
}
/**
 * GTFS levels.txt: one floor/level of a station, for indoor wayfinding.
 * @export
 * @interface Level
 */
export interface Level {
    /**
     * 
     */
    levelId: string;
    /**
     * 
     */
    levelIndex?: number | null;
    /**
     * 
     */
    levelName?: string | null;
}

/**
 * 
 * @export
 */
export const MatchStatus = {
    Pending: 'PENDING',
    Matched: 'MATCHED',
    Unmatched: 'UNMATCHED',
    Skipped: 'SKIPPED',
} as const;
export type MatchStatus = typeof MatchStatus[keyof typeof MatchStatus];

/**
 * One scheduled stop for a new trip created via addTrip, in stop_sequence order.
 * @export
 * @interface NewStopTimeInput
 */
export interface NewStopTimeInput {
    /**
     * 
     */
    stopId: string;
    /**
     * 
     */
    arrivalSec: number;
    /**
     * 
     */
    departureSec: number;
}
/**
 * One proposed schedule adjustment produced by an optimization run, backed by observed AVL evidence.
 * @export
 * @interface OptimizationRecommendation
 */
export interface OptimizationRecommendation {
    /**
     * 
     */
    id: string;
    /**
     * 
     */
    kind: OptimizationRecommendationKind;
    /**
     * 
     */
    status: OptimizationRecommendationStatus;
    /**
     * Number of observed samples this recommendation is based on.
     */
    sampleCount: number;
    /**
     * Proposed change in seconds, signed by kind (e.g. a stop-time or trip-start adjustment).
     */
    deltaSec: number;
    /**
     * The current schedule value(s) this recommendation would change.
     */
    currentValue: any | null;
    /**
     * The proposed replacement value(s).
     */
    proposedValue: any | null;
    /**
     * Supporting observed-data detail behind this recommendation.
     */
    evidence: any | null;
    /**
     * Human-readable explanation of why this recommendation was made.
     */
    reason: string;
}



/**
 * 
 * @export
 */
export const OptimizationRecommendationKind = {
    StopTime: 'STOP_TIME',
    TripShift: 'TRIP_SHIFT',
} as const;
export type OptimizationRecommendationKind = typeof OptimizationRecommendationKind[keyof typeof OptimizationRecommendationKind];


/**
 * 
 * @export
 */
export const OptimizationRecommendationStatus = {
    Pending: 'PENDING',
    Selected: 'SELECTED',
    Applied: 'APPLIED',
    Dismissed: 'DISMISSED',
    Unavailable: 'UNAVAILABLE',
} as const;
export type OptimizationRecommendationStatus = typeof OptimizationRecommendationStatus[keyof typeof OptimizationRecommendationStatus];

/**
 * One asynchronous schedule-optimization analysis, frozen to the feed's active revision at submission time.
 * @export
 * @interface OptimizationRun
 */
export interface OptimizationRun {
    /**
     * 
     */
    id: string;
    /**
     * The revision this run's recommendations apply against.
     */
    revisionId: string;
    /**
     * 
     */
    state: OptimizationRunState;
    /**
     * Failure detail when state is FAILED, or null otherwise.
     */
    error?: string | null;
    /**
     * 
     */
    createdAt: string;
    /**
     * ISO-8601 instant the run finished (successfully or not), or null while still QUEUED/RUNNING.
     */
    completedAt?: string | null;
}


/**
 * Request to analyze observed AVL data against a feed's current schedule for a window of trips, looking for timing adjustments.
 * @export
 * @interface OptimizationRunInput
 */
export interface OptimizationRunInput {
    /**
     * 
     */
    feedCode: string;
    /**
     * Restrict analysis to this service; omit to cover every service.
     */
    serviceId?: string | null;
    /**
     * 
     */
    routeId?: string | null;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * Restrict to trips starting at or after this time, in seconds since midnight.
     */
    windowFromSec?: number | null;
    /**
     * 
     */
    windowToSec?: number | null;
    /**
     * ISO-8601 instant
     */
    observedFrom: string;
    /**
     * ISO-8601 instant
     */
    observedTo: string;
    /**
     * Minimum number of observed samples required before a recommendation is produced for a given stop/trip.
     */
    minimumSamples: number;
}

/**
 * 
 * @export
 */
export const OptimizationRunState = {
    Queued: 'QUEUED',
    Running: 'RUNNING',
    Succeeded: 'SUCCEEDED',
    Failed: 'FAILED',
} as const;
export type OptimizationRunState = typeof OptimizationRunState[keyof typeof OptimizationRunState];

/**
 * GTFS pathways.txt: a connection (corridor, stairs, escalator, elevator, etc.) between two
 * stops/stations/entrances for indoor wayfinding. pathwayMode: 1=walkway, 2=stairs, 3=moving
 * sidewalk, 4=escalator, 5=elevator, 6=fare gate, 7=exit gate. isBidirectional: 0=unidirectional, 1=bidirectional.
 * @export
 * @interface Pathway
 */
export interface Pathway {
    /**
     * 
     */
    pathwayId: string;
    /**
     * 
     */
    fromStopId?: string | null;
    /**
     * 
     */
    toStopId?: string | null;
    /**
     * 
     */
    pathwayMode?: number | null;
    /**
     * 
     */
    isBidirectional?: number | null;
    /**
     * 
     */
    length?: number | null;
    /**
     * 
     */
    traversalTime?: number | null;
    /**
     * 
     */
    stairCount?: number | null;
    /**
     * 
     */
    maxSlope?: number | null;
    /**
     * 
     */
    minWidth?: number | null;
    /**
     * 
     */
    signpostedAs?: string | null;
    /**
     * 
     */
    reversedSignpostedAs?: string | null;
}
/**
 * Prediction error for snapshots made at most horizonSec (and more than the next shorter horizon) before the actual crossing.
 * @export
 * @interface PredictionAccuracyHorizon
 */
export interface PredictionAccuracyHorizon {
    /**
     * 
     */
    horizonSec: number;
    /**
     * 
     */
    sampleCount: number;
    /**
     * 
     */
    eligibleCount: number;
    /**
     * 
     */
    predictionCoverage: number;
    /**
     * 
     */
    pairedSampleCount: number;
    /**
     * Mean signed error in seconds (actual minus predicted); positive means vehicles got there later than predicted.
     */
    meanErrorSec?: number | null;
    /**
     * 
     */
    meanAbsErrorSec?: number | null;
    /**
     * 
     */
    p90AbsErrorSec?: number | null;
    /**
     * 
     */
    p95AbsErrorSec?: number | null;
    /**
     * 
     */
    confidenceCoverage?: number | null;
    /**
     * 
     */
    confidenceSampleCount: number;
}
/**
 * Aggregate prediction error for one algorithm over a recent time window. Each stop the vehicle reaches is scored once per lead-time horizon a prediction was made within, so the overall figures pool every horizon.
 * @export
 * @interface PredictionAccuracySummary
 */
export interface PredictionAccuracySummary {
    /**
     * 
     */
    algorithm: PredictionAlgorithm;
    /**
     * Available forecasts across eligible shared snapshots and all horizons.
     */
    sampleCount: number;
    /**
     * Eligible shared snapshots, including missing forecasts. Only reliable actual crossings are scored.
     */
    eligibleCount: number;
    /**
     * Available forecasts divided by eligibleCount; conditional on matched observations, not total scheduled service.
     */
    predictionCoverage: number;
    /**
     * Snapshots where every compared algorithm produced a forecast. Error statistics use only these paired samples.
     */
    pairedSampleCount: number;
    /**
     * Mean signed error in seconds (actual minus predicted); positive means vehicles got there later than predicted.
     */
    meanErrorSec?: number | null;
    /**
     * Mean absolute error in seconds.
     */
    meanAbsErrorSec?: number | null;
    /**
     * 
     */
    p90AbsErrorSec?: number | null;
    /**
     * 
     */
    p95AbsErrorSec?: number | null;
    /**
     * Observed fraction of errors within the algorithm's one-SD spread, among available forecasts with a spread.
     */
    confidenceCoverage?: number | null;
    /**
     * 
     */
    confidenceSampleCount: number;
    /**
     * The same statistics per lead-time horizon, shortest first.
     */
    byHorizon: Array<PredictionAccuracyHorizon>;
}



/**
 * 
 * @export
 */
export const PredictionAlgorithm = {
    ScheduleAdherence: 'SCHEDULE_ADHERENCE',
    HistoricalAverage: 'HISTORICAL_AVERAGE',
    Kalman: 'KALMAN',
} as const;
export type PredictionAlgorithm = typeof PredictionAlgorithm[keyof typeof PredictionAlgorithm];


/**
 * 
 * @export
 */
export const PredictionDayType = {
    Weekday: 'WEEKDAY',
    Saturday: 'SATURDAY',
    Sunday: 'SUNDAY',
} as const;
export type PredictionDayType = typeof PredictionDayType[keyof typeof PredictionDayType];


/**
 * 
 * @export
 */
export const PredictionTimeBand = {
    Night: 'NIGHT',
    MorningPeak: 'MORNING_PEAK',
    Midday: 'MIDDAY',
    AfternoonPeak: 'AFTERNOON_PEAK',
    Evening: 'EVENING',
} as const;
export type PredictionTimeBand = typeof PredictionTimeBand[keyof typeof PredictionTimeBand];

/**
 * 
 * @export
 * @interface Problem
 */
export interface Problem {
    /**
     * 
     */
    type?: string;
    /**
     * 
     */
    title: string;
    /**
     * 
     */
    status: number;
    /**
     * 
     */
    detail?: string;
    /**
     * 
     */
    instance?: string;
    /**
     * Stable application error code.
     */
    code?: string;
    /**
     * Current draft version for stale edit conflicts.
     */
    currentVersion?: number;
    /**
     * 
     */
    conflictingRecommendationIds?: Array<string>;
}
/**
 * 
 * @export
 * @interface RedoDraftEditRequest
 */
export interface RedoDraftEditRequest {
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
}
/**
 * 
 * @export
 * @interface RegisterFeedInput
 */
export interface RegisterFeedInput {
    /**
     * 
     */
    code: string;
    /**
     * 
     */
    name: string;
    /**
     * 
     */
    description?: string | null;
    /**
     * 
     */
    url: string;
    /**
     * 
     */
    pollingCron?: string | null;
    /**
     * 
     */
    enabled?: boolean | null;
    /**
     * If true, ingestFeed automatically activates a successfully validated revision instead of leaving it READY.
     */
    autoActivate?: boolean | null;
}
/**
 * 
 * @export
 * @interface RemoveTripStopInput
 */
export interface RemoveTripStopInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    stopSequence: number;
}
/**
 * 
 * @export
 * @interface ReorderTripStopsInput
 */
export interface ReorderTripStopsInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * Every stop ID of the trip, in the desired new order.
     */
    stopIdOrder: Array<string>;
}
/**
 * 
 * @export
 * @interface RevertDraftToForkRequest
 */
export interface RevertDraftToForkRequest {
    /**
     * 
     */
    editor: string;
}
/**
 * One ingest attempt (or draft) of a feed's GTFS data, with its validation and processing outcome.
 * @export
 * @interface Revision
 */
export interface Revision {
    /**
     * 
     */
    id: string;
    /**
     * 
     */
    feedCode: string;
    /**
     * 
     */
    status: RevisionStatus;
    /**
     * 
     */
    createdAt: string;
    /**
     * ISO-8601 instant this revision became ACTIVE, or null if it never was.
     */
    activatedAt?: string | null;
    /**
     * ISO-8601 instant this revision was superseded by a later activation, or null if it hasn't been.
     */
    supersededAt?: string | null;
    /**
     * SHA-256 of the downloaded GTFS zip, used to detect byte-identical re-ingests (see RevisionStatus.UNCHANGED).
     */
    contentSha256?: string | null;
    /**
     * Size of the downloaded GTFS zip in bytes.
     */
    byteSize?: number | null;
    /**
     * Names of the GTFS files present in this revision's zip.
     */
    filesPresent: Array<string>;
    /**
     * Number of rows loaded per GTFS table.
     */
    rowCounts?: any | null;
    /**
     * Earliest service date covered by this revision's calendars, per feed_info/calendar.
     */
    feedStartDate?: string | null;
    /**
     * Latest service date covered by this revision's calendars.
     */
    feedEndDate?: string | null;
    /**
     * Validation results from the most recent GTFS validator run against this revision, or null if it hasn't been validated.
     */
    validationSummary?: ValidationSummary | null;
    /**
     * Failure detail when status is FAILED, or null otherwise.
     */
    errorMessage?: string | null;
}



/**
 * 
 * @export
 */
export const RevisionStatus = {
    Pending: 'PENDING',
    Downloading: 'DOWNLOADING',
    Validating: 'VALIDATING',
    Parsing: 'PARSING',
    Deriving: 'DERIVING',
    Ready: 'READY',
    Active: 'ACTIVE',
    Superseded: 'SUPERSEDED',
    Failed: 'FAILED',
    Unchanged: 'UNCHANGED',
    Draft: 'DRAFT',
} as const;
export type RevisionStatus = typeof RevisionStatus[keyof typeof RevisionStatus];

/**
 * GTFS routes.txt: a named, user-facing service (e.g. a bus line). routeType follows the GTFS
 * extended route_type vocabulary (0=tram, 1=subway, 2=rail, 3=bus, 4=ferry, 5=cable tram, 6=aerial
 * lift, 7=funicular, 11=trolleybus, 12=monorail).
 * @export
 * @interface Route
 */
export interface Route {
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    agencyId?: string | null;
    /**
     * 
     */
    routeShortName?: string | null;
    /**
     * 
     */
    routeLongName?: string | null;
    /**
     * 
     */
    routeDesc?: string | null;
    /**
     * 
     */
    routeType?: number | null;
    /**
     * 
     */
    routeUrl?: string | null;
    /**
     * 
     */
    routeColor?: string | null;
    /**
     * 
     */
    routeTextColor?: string | null;
    /**
     * 
     */
    routeSortOrder?: number | null;
    /**
     * 
     */
    networkId?: string | null;
    /**
     * 
     */
    extent?: Extent | null;
}
/**
 * Aggregate schedule adherence for one route over a date range, from non-null StopPassage.adherenceSec samples.
 * @export
 * @interface RouteAdherenceSummary
 */
export interface RouteAdherenceSummary {
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    routeShortName?: string | null;
    /**
     * Number of non-null adherence samples; a route with zero samples produces no summary at all.
     */
    sampleCount: number;
    /**
     * 
     */
    meanAdherenceSec: number;
    /**
     * Percentage of samples within the on-time window, from 0 to 100.
     */
    onTimePct: number;
}
/**
 * The result of diffing two GTFS revisions' derived schedules.
 * @export
 * @interface ScheduleComparison
 */
export interface ScheduleComparison {
    /**
     * 
     */
    fromRevisionId: string;
    /**
     * 
     */
    toRevisionId: string;
    /**
     * True if fromRevisionId's derived schedule data is stale (needs a rebuild) at the time of comparison.
     */
    fromDerivationStale: boolean;
    /**
     * 
     */
    toDerivationStale: boolean;
    /**
     * Total number of trip changes found, independent of tripChanges' offset/limit pagination.
     */
    tripChangeCount: number;
    /**
     * A page of the trip-level changes, per the query's offset/limit.
     */
    tripChanges: Array<TripChange>;
    /**
     * 
     */
    calendarChanges: Array<CalendarChange>;
    /**
     * Per-route/direction/service headway comparison between the two revisions.
     */
    headwaySummaries: Array<HeadwaySummary>;
}
/**
 * Scheduled timing for one stop of one specific trip, as derived from its stop_times.
 * @export
 * @interface ScheduleTime
 */
export interface ScheduleTime {
    /**
     * GTFS trip_id this entry belongs to.
     */
    tripId: string;
    /**
     * 
     */
    stopPathIndex: number;
    /**
     * Scheduled arrival in seconds since midnight of the trip's service day, or null if not scheduled at this stop.
     */
    arrivalSec?: number | null;
    /**
     * 
     */
    departureSec?: number | null;
    /**
     * True if this time was interpolated (the trip's own stop_times didn't specify it directly) rather than taken as-is from GTFS.
     */
    interpolated: boolean;
    /**
     * Scheduled travel time from the previous stop, in seconds.
     */
    schedTravelTimeSec?: number | null;
    /**
     * 
     */
    schedDwellTimeSec?: number | null;
}
/**
 * 
 * @export
 * @interface SetCalendarExceptionInput
 */
export interface SetCalendarExceptionInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    date: string;
    /**
     * 1 = service added, 2 = service removed, null = delete the exception
     */
    exceptionType?: number | null;
}
/**
 * 
 * @export
 * @interface SetCalendarInput
 */
export interface SetCalendarInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    monday: boolean;
    /**
     * 
     */
    tuesday: boolean;
    /**
     * 
     */
    wednesday: boolean;
    /**
     * 
     */
    thursday: boolean;
    /**
     * 
     */
    friday: boolean;
    /**
     * 
     */
    saturday: boolean;
    /**
     * 
     */
    sunday: boolean;
    /**
     * 
     */
    startDate: string;
    /**
     * 
     */
    endDate: string;
}
/**
 * 
 * @export
 * @interface SetStopDwellInput
 */
export interface SetStopDwellInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    stopSequence: number;
    /**
     * Desired dwell time at this stop, in seconds.
     */
    dwellSec: number;
}

/**
 * 
 * @export
 */
export const Severity = {
    Error: 'ERROR',
    Warning: 'WARNING',
    Info: 'INFO',
} as const;
export type Severity = typeof Severity[keyof typeof Severity];

/**
 * GTFS shapes.txt: a trip's physical path, summarized from its points.
 * @export
 * @interface Shape
 */
export interface Shape {
    /**
     * 
     */
    shapeId: string;
    /**
     * 
     */
    pointCount: number;
    /**
     * 
     */
    lengthM?: number | null;
    /**
     * 
     */
    points: Array<ShapePoint>;
    /**
     * The whole shape as a GeoJSON LineString (coordinates are [lng, lat]), gzip-compressed then base64-encoded; null if it has fewer than two valid points. Prefer this over `points` for drawing.
     */
    geometry?: string | null;
}
/**
 * One point along a trip shape's geometry.
 * @export
 * @interface ShapePoint
 */
export interface ShapePoint {
    /**
     * 
     */
    lat?: number | null;
    /**
     * 
     */
    lon?: number | null;
    /**
     * 
     */
    sequence: number;
    /**
     * 
     */
    distTraveled?: number | null;
}
/**
 * 
 * @export
 * @interface ShiftTripInput
 */
export interface ShiftTripInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * Signed offset in seconds applied to every scheduled stop time on the trip.
     */
    deltaSec: number;
}
/**
 * GTFS stops.txt: a physical location vehicles stop at, or a station/entrance grouping such
 * locations. locationType: 0=stop/platform, 1=station, 2=entrance/exit, 3=generic node, 4=boarding
 * area. wheelchairBoarding: 0=unknown, 1=accessible, 2=not accessible.
 * @export
 * @interface Stop
 */
export interface Stop {
    /**
     * 
     */
    stopId: string;
    /**
     * 
     */
    stopCode?: string | null;
    /**
     * 
     */
    stopName?: string | null;
    /**
     * 
     */
    stopDesc?: string | null;
    /**
     * 
     */
    stopLat?: number | null;
    /**
     * 
     */
    stopLon?: number | null;
    /**
     * 
     */
    zoneId?: string | null;
    /**
     * 
     */
    locationType?: number | null;
    /**
     * 
     */
    parentStation?: string | null;
    /**
     * 
     */
    wheelchairBoarding?: number | null;
    /**
     * 
     */
    levelId?: string | null;
    /**
     * 
     */
    platformCode?: string | null;
}

/**
 * 
 * @export
 */
export const StopChangeKind = {
    StopAdded: 'STOP_ADDED',
    StopRemoved: 'STOP_REMOVED',
    StopTimeChanged: 'STOP_TIME_CHANGED',
} as const;
export type StopChangeKind = typeof StopChangeKind[keyof typeof StopChangeKind];

/**
 * One scheduled stop of one trip execution, as derived on-demand from AVL match history. observedSec/adherenceSec are null when the trip never reached this stop (by stopPathIndex) in the queried window.
 * @export
 * @interface StopPassage
 */
export interface StopPassage {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    blockId?: string | null;
    /**
     * The calendar service date this trip execution is inferred to belong to, which for a late-night trip may be the day before the AVL activity was observed.
     */
    serviceDate: string;
    /**
     * 
     */
    stopId: string;
    /**
     * 
     */
    stopName?: string | null;
    /**
     * Index into the trip's stop sequence.
     */
    stopPathIndex: number;
    /**
     * Scheduled arrival (or departure, if no arrival is scheduled) in seconds since midnight of serviceDate.
     */
    scheduledSec?: number | null;
    /**
     * When the vehicle was actually observed at this stop, in seconds since midnight of serviceDate, or null if never reached.
     */
    observedSec?: number | null;
    /**
     * Seconds behind (positive) or ahead of (negative) schedule when observed, or null if never reached. Not guaranteed to equal observedSec minus scheduledSec; see the interpolation note on the underlying match.
     */
    adherenceSec?: number | null;
    /**
     * The vehicle that served this trip execution; shared by every passage in the group even when observedSec is null.
     */
    vehicleId: string;
    /**
     * The trip's GTFS directionId, constant across every passage of this trip execution.
     */
    direction?: number | null;
    /**
     * Occupancy reported by the AVL ping that produced this passage's observedSec, or null if observedSec is null or the feed didn't report occupancy.
     */
    occupancyStatus?: AvlOccupancyStatus | null;
}


/**
 * One scheduled stop within a trip pattern, with derived timing and geometry metadata shared by every trip that follows the pattern.
 * @export
 * @interface StopPath
 */
export interface StopPath {
    /**
     * Index of this stop within the pattern's stop sequence; used throughout the API (Vehicle, AvlReport, predictions) to refer to a position along a trip.
     */
    stopPathIndex: number;
    /**
     * 
     */
    stopId: string;
    /**
     * The original GTFS stop_sequence this stop path was derived from.
     */
    stopSeq: number;
    /**
     * Distance in meters along the pattern's shape from the start of the trip to this stop.
     */
    lengthM: number;
    /**
     * GTFS pickup_type for this stop across the pattern's trips, if consistent.
     */
    pickupType?: number | null;
    /**
     * 
     */
    dropOffType?: number | null;
    /**
     * True if this is a scheduled waiting point (e.g. a layover) rather than a normal pass-through stop.
     */
    waitStop: boolean;
    /**
     * True if schedule adherence is meaningfully measured at this stop (e.g. a timepoint).
     */
    scheduleAdherenceStop: boolean;
    /**
     * True if trips pause here for a scheduled break (e.g. a terminal layover) rather than just a brief dwell.
     */
    layoverStop: boolean;
    /**
     * Scheduled break duration at this stop, in seconds, when layoverStop is true.
     */
    breakTimeSec?: number | null;
    /**
     * Typical observed or scheduled travel time from the previous stop, in seconds.
     */
    typicalTravelTimeSec?: number | null;
    /**
     * Typical observed or scheduled dwell time at this stop, in seconds.
     */
    typicalDwellTimeSec?: number | null;
    /**
     * Key of the trip pattern this stop path belongs to.
     */
    patternKey: string;
}
/**
 * A predicted (and, once reached, actual) arrival/departure at one scheduled stop of a vehicle's current trip.
 * @export
 * @interface StopPrediction
 */
export interface StopPrediction {
    /**
     * Index into the trip pattern's stopPaths this prediction is for.
     */
    stopPathIndex: number;
    /**
     * ISO-8601 instant of the scheduled arrival, or null if the trip has no scheduled arrival at this stop.
     */
    scheduledArrival?: string | null;
    /**
     * 
     */
    scheduledDeparture?: string | null;
    /**
     * ISO-8601 GPS-interpolated arrival, present once the stop has been reached.
     */
    actualArrival?: string | null;
    /**
     * 
     */
    actualDeparture?: string | null;
    /**
     * ISO-8601 predicted arrival, retained after arrival for comparison with the actual.
     */
    predictedArrival?: string | null;
    /**
     * 
     */
    predictedDeparture?: string | null;
    /**
     * The algorithm that produced this prediction, or null for a stop already reached (no prediction was needed).
     */
    algorithm?: PredictionAlgorithm | null;
    /**
     * Estimated arrival standard deviation in seconds (one SD), not a guaranteed confidence interval. Includes uncertainty of unlearned legs and ageing Kalman state.
     */
    confidenceSec?: number | null;
    /**
     * ISO-8601 timestamp of the AVL observation used to generate this prediction, independent of processing time.
     */
    sourceReportTs?: string | null;
    /**
     * Gap in seconds between GPS reports bracketing the actual crossing; null when unknown.
     */
    actualReportGapSec?: number | null;
    /**
     * Whether the actual crossing meets the configured report-gap quality limit; null when unknown.
     */
    actualReliable?: boolean | null;
    /**
     * GTFS stop_id of the stop this prediction is for.
     */
    stopId: string;
}


/**
 * GTFS stop_times.txt: one scheduled stop of one trip. arrivalTime/departureTime are the feed's
 * original HH:MM:SS strings (which may exceed 24:00:00 for a post-midnight trip); the *Seconds
 * fields are the same values normalized to seconds since midnight of the service day, for
 * arithmetic. pickupType/dropOffType: 0=regular, 1=none, 2=phone agency, 3=coordinate with driver.
 * timepoint: 0=approximate, 1=exact (default).
 * @export
 * @interface StopTime
 */
export interface StopTime {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    stopSequence: number;
    /**
     * 
     */
    stopId?: string | null;
    /**
     * 
     */
    arrivalTime?: string | null;
    /**
     * 
     */
    arrivalTimeSeconds?: number | null;
    /**
     * 
     */
    departureTime?: string | null;
    /**
     * 
     */
    departureTimeSeconds?: number | null;
    /**
     * 
     */
    stopHeadsign?: string | null;
    /**
     * 
     */
    pickupType?: number | null;
    /**
     * 
     */
    dropOffType?: number | null;
    /**
     * 
     */
    shapeDistTraveled?: number | null;
    /**
     * 
     */
    timepoint?: number | null;
}
/**
 * One scheduled stop's change within a changed trip.
 * @export
 * @interface StopTimeChange
 */
export interface StopTimeChange {
    /**
     * 
     */
    stopSequence: number;
    /**
     * 
     */
    kind: StopChangeKind;
    /**
     * The stop involved, or null for a STOP_TIME_CHANGED entry that didn't change which stop is at this sequence.
     */
    stopId?: string | null;
    /**
     * Change in scheduled arrival time in seconds, or null if not applicable.
     */
    arrivalDeltaSec?: number | null;
    /**
     * 
     */
    departureDeltaSec?: number | null;
}



/**
 * 
 * @export
 */
export const Table = {
    FareAttributes: 'FARE_ATTRIBUTES',
    FareRules: 'FARE_RULES',
    Timeframes: 'TIMEFRAMES',
    RiderCategories: 'RIDER_CATEGORIES',
    FareMedia: 'FARE_MEDIA',
    FareProducts: 'FARE_PRODUCTS',
    FareLegRules: 'FARE_LEG_RULES',
    FareLegJoinRules: 'FARE_LEG_JOIN_RULES',
    FareTransferRules: 'FARE_TRANSFER_RULES',
    Areas: 'AREAS',
    StopAreas: 'STOP_AREAS',
    Networks: 'NETWORKS',
    RouteNetworks: 'ROUTE_NETWORKS',
    LocationGroups: 'LOCATION_GROUPS',
    LocationGroupStops: 'LOCATION_GROUP_STOPS',
    Locations: 'LOCATIONS',
    BookingRules: 'BOOKING_RULES',
    Translations: 'TRANSLATIONS',
    Attributions: 'ATTRIBUTIONS',
} as const;
export type Table = typeof Table[keyof typeof Table];

/**
 * GTFS transfers.txt: a rule describing how a transfer between two stops/routes/trips should be treated. transferType: 0=recommended, 1=timed, 2=requires minimum time, 3=not possible.
 * @export
 * @interface Transfer
 */
export interface Transfer {
    /**
     * 
     */
    fromStopId?: string | null;
    /**
     * 
     */
    toStopId?: string | null;
    /**
     * 
     */
    fromRouteId?: string | null;
    /**
     * 
     */
    toRouteId?: string | null;
    /**
     * 
     */
    fromTripId?: string | null;
    /**
     * 
     */
    toTripId?: string | null;
    /**
     * 
     */
    transferType?: number | null;
    /**
     * 
     */
    minTransferTime?: number | null;
}
/**
 * GTFS trips.txt: one scheduled run of a route. directionId is GTFS's binary direction (0/1,
 * feed-defined meaning, typically outbound/inbound). wheelchairAccessible/bikesAllowed: 0=unknown,
 * 1=yes, 2=no. tripPatternId links to this trip's derived TripPattern, if one was computed.
 * @export
 * @interface Trip
 */
export interface Trip {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    serviceId: string;
    /**
     * 
     */
    tripHeadsign?: string | null;
    /**
     * 
     */
    tripShortName?: string | null;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * 
     */
    blockId?: string | null;
    /**
     * 
     */
    shapeId?: string | null;
    /**
     * 
     */
    wheelchairAccessible?: number | null;
    /**
     * 
     */
    bikesAllowed?: number | null;
    /**
     * Scheduled start of this trip, in seconds since midnight of its service day.
     */
    startTimeSec?: number | null;
    /**
     * 
     */
    endTimeSec?: number | null;
    /**
     * True if this trip is generated from a GTFS frequencies.txt entry rather than fixed stop_times.
     */
    frequencyBased?: boolean | null;
    /**
     * True if this trip has no derivable schedule (e.g. frequency-based with no fixed times).
     */
    noSchedule?: boolean | null;
    /**
     * Key of the trip's pattern (see trip-patterns), or null if the trip has none.
     */
    patternKey?: string | null;
}
/**
 * Aggregate schedule adherence for one trip execution (tripId + serviceDate) over a date range.
 * @export
 * @interface TripAdherenceSummary
 */
export interface TripAdherenceSummary {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    serviceDate: string;
    /**
     * 
     */
    sampleCount: number;
    /**
     * 
     */
    meanAdherenceSec: number;
    /**
     * 
     */
    onTimePct: number;
}
/**
 * One trip's change between the two compared revisions.
 * @export
 * @interface TripChange
 */
export interface TripChange {
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    kind: TripChangeKind;
    /**
     * Changed top-level trip fields, keyed by field name to {from, to} old/new value pairs.
     */
    fieldChanges: any | null;
    /**
     * Per-stop changes within this trip.
     */
    stopChanges: Array<StopTimeChange>;
    /**
     * Change in the trip's total scheduled run time in seconds (toRevisionId minus fromRevisionId), or null for an added/removed trip.
     */
    runTimeDeltaSec?: number | null;
}



/**
 * 
 * @export
 */
export const TripChangeKind = {
    Added: 'ADDED',
    Removed: 'REMOVED',
    Modified: 'MODIFIED',
} as const;
export type TripChangeKind = typeof TripChangeKind[keyof typeof TripChangeKind];

/**
 * A distinct sequence of stops shared by one or more trips of a route, derived from the GTFS stop_times. Vehicles are matched against patterns rather than individual trips.
 * @export
 * @interface TripPattern
 */
export interface TripPattern {
    /**
     * Stable identifier for this pattern within its revision.
     */
    patternKey: string;
    /**
     * 
     */
    routeId: string;
    /**
     * 
     */
    directionId?: number | null;
    /**
     * Representative headsign shared by this pattern's trips, if consistent.
     */
    headsign?: string | null;
    /**
     * 
     */
    shapeId?: string | null;
    /**
     * Number of scheduled stops in this pattern.
     */
    stopCount: number;
    /**
     * Total pattern length in meters, from its shape.
     */
    lengthM?: number | null;
    /**
     * Number of trips that follow this pattern.
     */
    tripCount: number;
    /**
     * Geographic bounding box of this pattern's shape.
     */
    extent?: Extent | null;
    /**
     * The pattern's whole polyline as a GeoJSON LineString (coordinates are [lng, lat]), gzip-compressed then base64-encoded; null if the pattern has fewer than two usable points.
     */
    geometry?: string | null;
}
/**
 * 
 * @export
 * @interface UndoDraftEditRequest
 */
export interface UndoDraftEditRequest {
    /**
     * 
     */
    editor: string;
    /**
     * 
     */
    expectedVersion: number;
}
/**
 * 
 * @export
 * @interface UpdateFeedInput
 */
export interface UpdateFeedInput {
    /**
     * 
     */
    name: string;
    /**
     * 
     */
    description?: string | null;
    /**
     * 
     */
    url: string;
    /**
     * 
     */
    pollingCron?: string | null;
    /**
     * 
     */
    enabled?: boolean | null;
    /**
     * 
     */
    autoActivate?: boolean | null;
}
/**
 * 
 * @export
 * @interface UpdateStopTimeInput
 */
export interface UpdateStopTimeInput {
    /**
     * 
     */
    draftId: string;
    /**
     * 
     */
    editor: string;
    /**
     * The draft's version this edit is based on; used to detect concurrent edits from another editor.
     */
    expectedVersion: number;
    /**
     * 
     */
    tripId: string;
    /**
     * 
     */
    stopSequence: number;
    /**
     * null clears the value; omitting is the same as null
     */
    arrivalSec?: number | null;
    /**
     * null clears the value; omitting is the same as null
     */
    departureSec?: number | null;
}
/**
 * One analyzer finding on a specific GTFS entity (not rolled up per rule): where it is, what was
 * observed versus expected, and how to fix it.
 * @export
 * @interface ValidationFinding
 */
export interface ValidationFinding {
    /**
     * 
     */
    ruleId: string;
    /**
     * 
     */
    title: string;
    /**
     * 
     */
    severity: FindingSeverity;
    /**
     * SPEC, INTEROP, QUALITY or ANALYTICS.
     */
    ruleClass: string;
    /**
     * Feed, File, Agency, Stop, Route, Trip, Shape, Service, Fare, Transfer, Pathway, Level, ...
     */
    entityType: string;
    /**
     * 
     */
    entityId?: string | null;
    /**
     * Set on trip-scoped findings.
     */
    serviceId?: string | null;
    /**
     * Source GTFS file, e.g. stop_times.txt.
     */
    file?: string | null;
    /**
     * 1-based row in `file`.
     */
    line?: number | null;
    /**
     * 
     */
    field?: string | null;
    /**
     * 
     */
    observedValue?: string | null;
    /**
     * 
     */
    expectedValue?: string | null;
    /**
     * Rule-specific context, e.g. distances or speeds.
     */
    details?: any | null;
    /**
     * 
     */
    message: string;
    /**
     * 
     */
    remediation: string;
}


/**
 * One distinct validation rule violation found in a revision, with a sample affected row.
 * @export
 * @interface ValidationNotice
 */
export interface ValidationNotice {
    /**
     * The validator rule that fired (e.g. a MobilityData GTFS validator notice code).
     */
    rule: string;
    /**
     * 
     */
    severity: Severity;
    /**
     * Number of rows this rule fired on.
     */
    count: number;
    /**
     * One example of an affected row, for diagnosing the issue.
     */
    sample: string;
}


/**
 * Aggregate GTFS validation results for one revision.
 * @export
 * @interface ValidationSummary
 */
export interface ValidationSummary {
    /**
     * 
     */
    errorCount: number;
    /**
     * 
     */
    warningCount: number;
    /**
     * Individual validation findings, one per distinct rule/severity combination that fired, with how many rows it affected.
     */
    notices: Array<ValidationNotice>;
}
/**
 * A vehicle's latest known position and match state on an AVL feed, updated on every poll.
 * @export
 * @interface Vehicle
 */
export interface Vehicle {
    /**
     * 
     */
    vehicleId: string;
    /**
     * Operator-facing label for the vehicle, if the feed provides one; often distinct from vehicleId.
     */
    label?: string | null;
    /**
     * ISO-8601 instant this position was reported by the vehicle.
     */
    reportTs: string;
    /**
     * Raw reported position.
     */
    position: LatLon;
    /**
     * Position snapped onto the matched trip's shape, or null while unmatched.
     */
    snappedPosition?: LatLon | null;
    /**
     * Compass heading in degrees, if reported.
     */
    bearing?: number | null;
    /**
     * 
     */
    speedMps?: number | null;
    /**
     * 
     */
    occupancyStatus?: AvlOccupancyStatus | null;
    /**
     * True once this vehicle has been matched to a trip.
     */
    matched: boolean;
    /**
     * True if this vehicle's last report is older than the feed considers live; the position shown may no longer reflect where the vehicle actually is.
     */
    stale: boolean;
    /**
     * Seconds behind (positive) or ahead of (negative) schedule at the matched position, or null while unmatched.
     */
    scheduleAdherenceSec?: number | null;
    /**
     * Index into the matched trip pattern's stopPaths the vehicle is currently progressing through, or null while unmatched.
     */
    stopPathIndex?: number | null;
    /**
     * Distance in meters the vehicle has traveled along its matched trip's shape, or null while unmatched.
     */
    distanceAlongTripM?: number | null;
    /**
     * GTFS revision the vehicle is matched against; pass it as revisionId when reading its trip or stop, or null while unmatched.
     */
    revisionId?: string | null;
    /**
     * GTFS trip_id of the matched trip (read it via the trips endpoint), or null while unmatched.
     */
    tripId?: string | null;
    /**
     * GTFS stop_id of the stop the vehicle is currently at or approaching, or null while unmatched.
     */
    currentStopId?: string | null;
}



/**
 * 
 * @export
 */
export const VehicleStopStatus = {
    IncomingAt: 'INCOMING_AT',
    StoppedAt: 'STOPPED_AT',
    InTransitTo: 'IN_TRANSIT_TO',
} as const;
export type VehicleStopStatus = typeof VehicleStopStatus[keyof typeof VehicleStopStatus];

