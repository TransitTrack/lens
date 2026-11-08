// View-specific response and variable types, backed by the OpenAPI-generated models.
import type * as Rest from '~/api'

type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] }
type InputView<T> = { [K in keyof T]: undefined extends T[K] ? T[K] | null : T[K] }
export type AddTripInput = InputView<Rest.AddTripInput>
export type AvlFormat = Rest.AvlFormat
export type AvlOccupancyStatus = Rest.AvlOccupancyStatus
export type BulkShiftTripsInput = InputView<Rest.BulkShiftTripsInput>
export type CalendarChangeKind = Rest.CalendarChangeKind
export type DeleteTripInput = InputView<Rest.DeleteTripInput>
export type DraftJobPhase = Rest.DraftJobPhase
export type DraftJobState = Rest.DraftJobState
export type DraftKind = Rest.DraftKind
export type DuplicateTripInput = InputView<Rest.DuplicateTripInput>
export type ExceptionChangeKind = Rest.ExceptionChangeKind
export type FeedSource = Rest.FeedSource
export type FindingSeverity = Rest.FindingSeverity
export type ForkDraftInput = InputView<Rest.ForkDraftInput>
export type MatchStatus = Rest.MatchStatus
export type OptimizationRecommendationKind = Rest.OptimizationRecommendationKind
export type OptimizationRecommendationStatus = Rest.OptimizationRecommendationStatus
export type OptimizationRunInput = InputView<Rest.OptimizationRunInput>
export type OptimizationRunState = Rest.OptimizationRunState
export type PredictionAlgorithm = Rest.PredictionAlgorithm
export type RevisionStatus = Rest.RevisionStatus
export type SetStopDwellInput = InputView<Rest.SetStopDwellInput>
export type Severity = Rest.Severity
export type ShiftTripInput = InputView<Rest.ShiftTripInput>
export type StopChangeKind = Rest.StopChangeKind
export type TripChangeKind = Rest.TripChangeKind
export type UpdateFeedInput = InputView<Rest.UpdateFeedInput>
export type UpdateStopTimeInput = InputView<Rest.UpdateStopTimeInput>
export type VehicleStopStatus = Rest.VehicleStopStatus

export type ActivateDraftMutation = { activateDraft: { id: string, status: RevisionStatus } };

export type ActivateDraftMutationVariables = Exact<{
  id: string | number;
  editor: string;
  force?: boolean | null | undefined;
}>;

export type ActivateRevisionMutation = { activateRevision: { id: string, status: RevisionStatus } };

export type ActivateRevisionMutationVariables = Exact<{
  revisionId: string | number;
}>;

export type AddTripMutation = { addTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type AddTripMutationVariables = Exact<{
  input: AddTripInput;
}>;

export type AgenciesDetailQuery = { agencies: Array<{ agencyId: string | null, agencyName: string | null, agencyUrl: string | null, agencyTimezone: string | null, agencyPhone: string | null, agencyEmail: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AgenciesDetailQueryVariables = Exact<{
  feedCode: string;
}>;

export type AgenciesQuery = { agencies: Array<{ agencyId: string | null, extent: { minLat: number, minLon: number, maxLat: number, maxLon: number } | null }> };

export type AgenciesQueryVariables = Exact<{
  feedCode: string;
}>;

export type AllRoutesAdherenceQuery = { allRoutesAdherence: Array<{ routeId: string, routeShortName: string | null, sampleCount: number, meanAdherenceSec: number, onTimePct: number }> };

export type AllRoutesAdherenceQueryVariables = Exact<{
  feedCode: string;
  startDate: string;
  endDate: string;
}>;

export type AllTripsAdherenceQuery = { allTripsAdherence: Array<{ tripId: string, routeId: string, serviceDate: string, sampleCount: number, meanAdherenceSec: number, onTimePct: number }> };

export type AllTripsAdherenceQueryVariables = Exact<{
  feedCode: string;
  routeId?: string | null | undefined;
  startDate: string;
  endDate: string;
}>;

export type ApplyOptimizationRecommendationsMutation = { applyOptimizationRecommendations: { id: string, feedCode: string, label: string | null, status: RevisionStatus, kind: DraftKind } };

export type ApplyOptimizationRecommendationsMutationVariables = Exact<{
  runId: string | number;
  recommendationIds: Array<string | number> | string | number;
  label?: string | null | undefined;
  editor: string;
}>;

export type AvlFeedsQuery = { avlFeeds: Array<{ code: string, name: string, gtfsFeedCode: string, format: AvlFormat, predictionAlgorithms: Array<PredictionAlgorithm>, enabled: boolean, pollIntervalSec: number, lastPollAt: string | null, lastPollStatus: string | null, lastPollReportCount: number | null }> };

export type AvlLogQuery = { avlReports: Array<{ ts: string, speedMps: number | null, bearing: number | null, matchStatus: MatchStatus, descTripId: string | null, position: { lat: number, lon: number }, match: { stopPathIndex: number, distanceAlongTripM: number, deviationM: number, scheduleAdherenceSec: number | null, score: number | null, trip: { tripId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null } | null } | null } | null }> };

export type AvlLogQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  limit?: number | null | undefined;
}>;

export type AvlReportCatalogQuery = { avlReportCatalog: { timeZone: string, vehicleIds: Array<string> } };

export type AvlReportCatalogQueryVariables = Exact<{
  feedCode: string;
}>;

export type AvlReportPageQuery = { avlReportPage: { totalCount: number, page: number, pageSize: number, items: Array<{ vehicleId: string, ts: string, processedAt: string, source: AvlFormat, speedMps: number | null, descTripId: string | null, position: { lat: number, lon: number }, match: { trip: { tripId: string } | null } | null }> } };

export type AvlReportPageQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  date: string;
  startTime: string;
  endTime: string;
  page?: number | null | undefined;
  pageSize?: number | null | undefined;
}>;

export type AvlTrailQuery = { avlReports: Array<{ vehicleId: string, ts: string, speedMps: number | null, bearing: number | null, currentStatus: VehicleStopStatus | null, occupancyStatus: AvlOccupancyStatus | null, descTripId: string | null, position: { lat: number, lon: number }, match: { distanceAlongTripM: number, scheduleAdherenceSec: number | null, trip: { tripId: string } | null } | null }> };

export type AvlTrailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
  limit?: number | null | undefined;
}>;

export type BlockDetailQuery = { block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string>, blockTrips: Array<{ listIndex: number, layoverAfterSec: number | null, deadheadAfter: boolean | null, trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeColor: string | null, routeTextColor: string | null } | null } }> } | null };

export type BlockDetailQueryVariables = Exact<{
  feedCode: string;
  blockId: string;
  serviceId: string;
}>;

export type BlocksListQuery = { blocks: Array<{ blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, tripCount: number, routeIds: Array<string>, trips: Array<{ routeId: string }> }> };

export type BlocksListQueryVariables = Exact<{
  feedCode: string;
}>;

export type BulkShiftTripsMutation = { bulkShiftTrips: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type BulkShiftTripsMutationVariables = Exact<{
  input: BulkShiftTripsInput;
}>;

export type ClaimDraftEditorMutation = { claimDraftEditor: { editor: string, expiresAt: string } };

export type ClaimDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
  takeOver?: boolean | null | undefined;
}>;

export type CompareRevisionsQuery = { compareRevisions: { fromRevisionId: string, toRevisionId: string, fromDerivationStale: boolean, toDerivationStale: boolean, tripChangeCount: number, tripChanges: Array<{ tripId: string, kind: TripChangeKind, fieldChanges: unknown, runTimeDeltaSec: number | null, stopChanges: Array<{ stopSequence: number, kind: StopChangeKind, stopId: string | null, arrivalDeltaSec: number | null, departureDeltaSec: number | null }> }>, calendarChanges: Array<{ serviceId: string, kind: CalendarChangeKind, fieldChanges: unknown, exceptionChanges: Array<{ date: string, kind: ExceptionChangeKind, fromType: number | null, toType: number | null }> }>, headwaySummaries: Array<{ routeId: string, directionId: number | null, serviceId: string, fromTripCount: number, toTripCount: number, fromMeanGapSec: number | null, toMeanGapSec: number | null, fromMaxGapSec: number | null, toMaxGapSec: number | null }> } };

export type CompareRevisionsQueryVariables = Exact<{
  fromRevisionId: string | number;
  toRevisionId: string | number;
  routeId?: string | null | undefined;
  directionId?: number | null | undefined;
  serviceId?: string | null | undefined;
  offset?: number | null | undefined;
  limit?: number | null | undefined;
}>;

export type DeleteRevisionMutation = { deleteRevision: boolean };

export type DeleteRevisionMutationVariables = Exact<{
  revisionId: string | number;
}>;

export type DeleteTripMutation = { deleteTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type DeleteTripMutationVariables = Exact<{
  input: DeleteTripInput;
}>;

export type DiscardDraftMutation = { discardDraft: boolean };

export type DiscardDraftMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;

export type DraftDetailQuery = { draft: { id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: RevisionStatus, kind: DraftKind, version: number, derivationStale: boolean, lastValidation: unknown, createdBy: string | null, createdAt: string, rowCounts: unknown, lock: { editor: string, expiresAt: string } | null } | null };

export type DraftDetailQueryVariables = Exact<{
  id: string | number;
}>;

export type DraftEditResultFFragment = { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null };

export type DraftEditsQuery = { draftEdits: Array<{ seq: number, op: string, summary: string, appliedAt: string, undone: boolean }> };

export type DraftEditsQueryVariables = Exact<{
  id: string | number;
  limit?: number | null | undefined;
}>;

export type DraftGridQuery = { draftGrid: { stops: Array<{ stopSequence: number, stopId: string | null, stopName: string | null, timepoint: number | null }>, trips: Array<{ tripId: string, firstDepartureSec: number | null, headsign: string | null, blockId: string | null, directionId: number | null, serviceId: string | null, cells: Array<{ stopSequence: number, arrivalSec: number | null, departureSec: number | null }> }> } };

export type DraftGridQueryVariables = Exact<{
  draftId: string | number;
  routeId: string;
  directionId?: number | null | undefined;
  serviceId?: string | null | undefined;
}>;

export type DraftJobQuery = { draftJob: { id: string, state: DraftJobState, phase: DraftJobPhase, error: string | null } | null };

export type DraftJobQueryVariables = Exact<{
  jobId: string | number;
}>;

export type DraftPatternsQuery = { tripPatterns: Array<{ patternKey: string, directionId: number | null, headsign: string | null, stopCount: number, stopPaths: Array<{ stopPathIndex: number, stopId: string, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null }> }> };

export type DraftPatternsQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
  revisionId: string | number;
}>;

export type DraftTripDetailQuery = { trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, serviceId: string, blockId: string | null, shapeId: string | null, startTimeSec: number | null, endTimeSec: number | null, frequencyBased: boolean | null, noSchedule: boolean | null, pattern: { patternKey: string, stopCount: number, lengthM: number | null } | null, block: { blockId: string, serviceId: string } | null } | null };

export type DraftTripDetailQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
  revisionId: string | number;
}>;

export type DraftsQuery = { drafts: Array<{ id: string, feedCode: string, label: string | null, baseRevisionId: string | null, status: RevisionStatus, kind: DraftKind, version: number, derivationStale: boolean, lastValidation: unknown, createdBy: string | null, createdAt: string, rowCounts: unknown, lock: { editor: string, expiresAt: string } | null }> };

export type DraftsQueryVariables = Exact<{
  feedCode?: string | null | undefined;
}>;

export type DuplicateTripMutation = { duplicateTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type DuplicateTripMutationVariables = Exact<{
  input: DuplicateTripInput;
}>;

export type ExploreCalendarQuery = { calendars: Array<{ serviceId: string, monday: boolean | null, tuesday: boolean | null, wednesday: boolean | null, thursday: boolean | null, friday: boolean | null, saturday: boolean | null, sunday: boolean | null, startDate: string | null, endDate: string | null }>, calendarDates: Array<{ serviceId: string, date: string, exceptionType: number | null }> };

export type ExploreCalendarQueryVariables = Exact<{
  feedCode: string;
}>;

export type ExplorePatternsQuery = { tripPatterns: Array<{ patternKey: string, routeId: string, directionId: number | null, headsign: string | null, tripCount: number, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null, routeType: number | null, agencyId: string | null } | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null }> }> };

export type ExplorePatternsQueryVariables = Exact<{
  feedCode: string;
}>;

export type ExploreRoutesQuery = { routes: Array<{ routeId: string, routeShortName: string | null, routeLongName: string | null, routeType: number | null, routeColor: string | null, routeTextColor: string | null, agencyId: string | null }>, agencies: Array<{ agencyId: string | null, agencyName: string | null }> };

export type ExploreRoutesQueryVariables = Exact<{
  feedCode: string;
}>;

export type ExploreStopsQuery = { stops: Array<{ stopId: string, stopName: string | null, stopCode: string | null, stopDesc: string | null, stopLat: number | null, stopLon: number | null, locationType: number | null, parentStation: string | null }> };

export type ExploreStopsQueryVariables = Exact<{
  feedCode: string;
}>;

export type FeedDetailQuery = { feed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean, source: FeedSource, activeRevision: { id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, filesPresent: Array<string>, rowCounts: unknown, validationSummary: { errorCount: number, warningCount: number, notices: Array<{ rule: string, severity: Severity, count: number, sample: string }> } | null } | null, revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null, supersededAt: string | null, feedStartDate: string | null, feedEndDate: string | null, byteSize: number | null, contentSha256: string | null, errorMessage: string | null, validationSummary: { errorCount: number, warningCount: number, notices: Array<{ rule: string, severity: Severity, count: number, sample: string }> } | null }> } | null, feedInfo: { feedPublisherName: string | null, feedPublisherUrl: string | null, feedLang: string | null, feedStartDate: string | null, feedEndDate: string | null, feedVersion: string | null, feedContactEmail: string | null, feedContactUrl: string | null } | null };

export type FeedDetailQueryVariables = Exact<{
  code: string;
}>;

export type FeedReadinessQuery = { feed: { code: string, name: string, activeRevision: { id: string } | null, revisions: Array<{ id: string, status: RevisionStatus }> } | null };

export type FeedReadinessQueryVariables = Exact<{
  code: string;
}>;

export type FeedsQuery = { feeds: Array<{ code: string, name: string, description: string | null, enabled: boolean, source: FeedSource, activeRevision: { id: string, status: RevisionStatus, feedStartDate: string | null, feedEndDate: string | null } | null }> };

export type ForkDraftMutation = { forkDraft: { id: string, feedCode: string, label: string | null, version: number } };

export type ForkDraftMutationVariables = Exact<{
  input: ForkDraftInput;
}>;

export type HeadwayInfoQuery = { headway: { stopId: string, routeId: string, directionId: number | null, waitSec: number | null, gapsSec: Array<number>, scheduledHeadwaySec: number | null } };

export type HeadwayInfoQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  routeId: string;
  directionId?: number | null | undefined;
  algorithm?: PredictionAlgorithm | null | undefined;
}>;

export type HeadwayRoutePatternsQuery = { tripPatterns: Array<{ patternKey: string, directionId: number | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, stop: { stopName: string | null } | null }> }> };

export type HeadwayRoutePatternsQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;

export type IngestFeedMutation = { ingestFeed: { id: string, status: RevisionStatus } };

export type IngestFeedMutationVariables = Exact<{
  feedCode: string;
}>;

export type OptimizationRecommendationsQuery = { optimizationRecommendations: Array<{ id: string, kind: OptimizationRecommendationKind, status: OptimizationRecommendationStatus, sampleCount: number, deltaSec: number, currentValue: unknown, proposedValue: unknown, evidence: unknown, reason: string }> };

export type OptimizationRecommendationsQueryVariables = Exact<{
  runId: string | number;
  status?: OptimizationRecommendationStatus | null | undefined;
  offset?: number | null | undefined;
  limit?: number | null | undefined;
}>;

export type OptimizationRunQuery = { optimizationRun: { id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null } | null };

export type OptimizationRunQueryVariables = Exact<{
  id: string | number;
}>;

export type OptimizationRunsQuery = { optimizationRuns: Array<{ id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null }> };

export type OptimizationRunsQueryVariables = Exact<{
  feedCode: string;
  limit?: number | null | undefined;
  offset?: number | null | undefined;
}>;

export type RebuildDraftMutation = { rebuildDraft: { id: string, state: DraftJobState, phase: DraftJobPhase, error: string | null } };

export type RebuildDraftMutationVariables = Exact<{
  id: string | number;
}>;

export type RedoDraftEditMutation = { redoDraftEdit: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type RedoDraftEditMutationVariables = Exact<{
  id: string | number;
  editor: string;
  expectedVersion: number;
}>;

export type ReleaseDraftEditorMutation = { releaseDraftEditor: boolean };

export type ReleaseDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;

export type RenewDraftEditorMutation = { renewDraftEditor: { editor: string, expiresAt: string } };

export type RenewDraftEditorMutationVariables = Exact<{
  id: string | number;
  editor: string;
}>;

export type RevisionsQuery = { revisions: Array<{ id: string, status: RevisionStatus, createdAt: string, activatedAt: string | null }> };

export type RevisionsQueryVariables = Exact<{
  feedCode: string;
}>;

export type RouteAdherenceQuery = { routeAdherence: Array<{ tripId: string, serviceDate: string, stopId: string, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null }> };

export type RouteAdherenceQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
  startDate: string;
  endDate: string;
}>;

export type RouteDetailQuery = { route: { routeId: string, routeShortName: string | null, routeLongName: string | null, routeDesc: string | null, routeType: number | null, routeColor: string | null, routeTextColor: string | null, agencyId: string | null } | null, tripPatterns: Array<{ patternKey: string, directionId: number | null, headsign: string | null, shapeId: string | null, stopCount: number, tripCount: number, lengthM: number | null, geometry: string | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, lengthM: number, pickupType: number | null, dropOffType: number | null, waitStop: boolean, scheduleAdherenceStop: boolean, layoverStop: boolean, breakTimeSec: number | null, typicalTravelTimeSec: number | null, typicalDwellTimeSec: number | null, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> }> };

export type RouteDetailQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;

export type RoutesQuery = { routes: Array<{ routeId: string, routeShortName: string | null, routeLongName: string | null }> };

export type RoutesQueryVariables = Exact<{
  feedCode: string;
}>;

export type SetStopDwellMutation = { setStopDwell: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type SetStopDwellMutationVariables = Exact<{
  input: SetStopDwellInput;
}>;

export type ShiftTripMutation = { shiftTrip: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type ShiftTripMutationVariables = Exact<{
  input: ShiftTripInput;
}>;

export type StartOptimizationRunMutation = { startOptimizationRun: { id: string, revisionId: string, state: OptimizationRunState, error: string | null, createdAt: string, completedAt: string | null } };

export type StartOptimizationRunMutationVariables = Exact<{
  input: OptimizationRunInput;
}>;

export type StopAdherenceQuery = { stopAdherence: Array<{ tripId: string, routeId: string, serviceDate: string, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null }> };

export type StopAdherenceQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  startDate: string;
  endDate: string;
}>;

export type StopBoardQuery = { stopPredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, predictedArrival: string | null, actualArrival: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null }> };

export type StopBoardQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
  routeId?: string | null | undefined;
  directionId?: number | null | undefined;
  algorithm?: PredictionAlgorithm | null | undefined;
}>;

export type StopDetailQuery = { stop: { stopId: string, stopName: string | null, stopCode: string | null, stopDesc: string | null, stopLat: number | null, stopLon: number | null, zoneId: string | null, locationType: number | null, parentStation: string | null, wheelchairBoarding: number | null, platformCode: string | null } | null };

export type StopDetailQueryVariables = Exact<{
  feedCode: string;
  stopId: string;
}>;

export type StopsQuery = { stops: Array<{ stopId: string, stopName: string | null, stopCode: string | null }> };

export type StopsQueryVariables = Exact<{
  feedCode: string;
}>;

export type TripAdherenceQuery = { tripAdherence: Array<{ serviceDate: string, stopId: string, stopName: string | null, stopPathIndex: number, scheduledSec: number | null, observedSec: number | null, adherenceSec: number | null, vehicleId: string, direction: number | null, occupancyStatus: AvlOccupancyStatus | null }> };

export type TripAdherenceQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
  startDate: string;
  endDate: string;
}>;

export type TripDetailQuery = { trip: { tripId: string, tripHeadsign: string | null, routeId: string, directionId: number | null, serviceId: string, shapeId: string | null, startTimeSec: number | null, endTimeSec: number | null, frequencyBased: boolean | null, noSchedule: boolean | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, pattern: { patternKey: string, stopCount: number, lengthM: number | null } | null, block: { blockId: string, serviceId: string } | null, scheduleTimes: Array<{ stopPathIndex: number, arrivalSec: number | null, departureSec: number | null, interpolated: boolean, schedTravelTimeSec: number | null, schedDwellTimeSec: number | null }>, stopTimes: Array<{ stopSequence: number, arrivalTime: string | null, departureTime: string | null, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }>, shape: { points: Array<{ lat: number | null, lon: number | null }> } | null } | null };

export type TripDetailQueryVariables = Exact<{
  feedCode: string;
  tripId: string;
}>;

export type TripsByRouteQuery = { trips: Array<{ tripId: string, tripHeadsign: string | null, tripShortName: string | null, directionId: number | null, serviceId: string, shapeId: string | null, blockId: string | null, startTimeSec: number | null, endTimeSec: number | null }> };

export type TripsByRouteQueryVariables = Exact<{
  feedCode: string;
  routeId: string;
}>;

export type UndoDraftEditMutation = { undoDraftEdit: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type UndoDraftEditMutationVariables = Exact<{
  id: string | number;
  editor: string;
  expectedVersion: number;
}>;

export type UpdateFeedMutation = { updateFeed: { code: string, name: string, description: string | null, url: string, pollingCron: string | null, enabled: boolean } };

export type UpdateFeedMutationVariables = Exact<{
  code: string;
  input: UpdateFeedInput;
}>;

export type UpdateStopTimeMutation = { updateStopTime: { canUndo: boolean, canRedo: boolean, draft: { id: string, version: number, derivationStale: boolean, lastValidation: unknown, lock: { editor: string, expiresAt: string } | null }, edit: { seq: number, op: string, summary: string, appliedAt: string, undone: boolean } | null } };

export type UpdateStopTimeMutationVariables = Exact<{
  input: UpdateStopTimeInput;
}>;

export type ValidationFindingCountsQuery = { validationFindingCounts: Array<{ entityId: string, count: number, worstSeverity: FindingSeverity }> };

export type ValidationFindingCountsQueryVariables = Exact<{
  feedCode: string;
  entityType: string;
}>;

export type ValidationFindingsQuery = { validationFindings: Array<{ ruleId: string, title: string, severity: FindingSeverity, ruleClass: string, entityType: string, entityId: string | null, serviceId: string | null, file: string | null, line: number | null, field: string | null, observedValue: string | null, expectedValue: string | null, details: unknown, message: string, remediation: string }> };

export type ValidationFindingsQueryVariables = Exact<{
  feedCode: string;
  entityType?: string | null | undefined;
  entityId?: string | null | undefined;
}>;

export type VehicleDetailQuery = { vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: AvlOccupancyStatus | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null, block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, blockTrips: Array<{ listIndex: number, trip: { tripId: string, tripHeadsign: string | null } }> } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { geometry: string | null } | null } | null, pattern: { patternKey: string, lengthM: number | null, geometry: string | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, lengthM: number, scheduleAdherenceStop: boolean, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null, vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, scheduledDeparture: string | null, predictedArrival: string | null, predictedDeparture: string | null, actualArrival: string | null, actualDeparture: string | null, actualReportGapSec: number | null, actualReliable: boolean | null, sourceReportTs: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>, predictionAccuracy: Array<{ algorithm: PredictionAlgorithm, sampleCount: number, meanErrorSec: number | null, meanAbsErrorSec: number | null, eligibleCount: number, predictionCoverage: number, pairedSampleCount: number, p90AbsErrorSec: number | null, p95AbsErrorSec: number | null, confidenceCoverage: number | null, confidenceSampleCount: number }> };

export type VehicleDetailQueryVariables = Exact<{
  feedCode: string;
  vehicleId: string;
}>;

