import { defineQueryOptions } from '@pinia/colada'
import { type Block, type BlockTrip, optional, Schedule, type ScheduleComparison, type ScheduleTime, type StopPath, type StopSchedule, type TripPattern } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../keys/feeds'
import { cachePolicy, revisionParam } from './cachePolicy'
import { compareKeys } from '../keys/schedule'

/** GET /feeds/{feedCode}/blocks/{blockId} — one Block of a service, or null when unknown. */
export const blockQuery = defineQueryOptions((p: Rev & { blockId: string, serviceId: string }) => ({
  key: feedKeys.resource(p, 'blocks', { blockId: p.blockId, serviceId: p.serviceId }),
  query: ({ signal }): Promise<Block | null> =>
    optional(Schedule.block({ path: { feedCode: p.feedCode, blockId: p.blockId }, query: { serviceId: p.serviceId, revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/block-trips — every BlockTrip, optionally of one block / service (all pages). */
export const blockTripsQuery = defineQueryOptions((p: Rev & { blockId?: string, serviceId?: string }) => ({
  key: feedKeys.resource(p, 'block-trips', { blockId: p.blockId, serviceId: p.serviceId }),
  query: ({ signal }): Promise<BlockTrip[]> =>
    fetchAllPages((offset, limit) => Schedule.blockTrips({ path: { feedCode: p.feedCode }, query: { blockId: p.blockId, serviceId: p.serviceId, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/blocks — every Block of the revision (all pages). */
export const blocksQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'blocks'),
  query: ({ signal }): Promise<Block[]> =>
    fetchAllPages((offset, limit) => Schedule.blocks({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

export interface CompareRevisionsParams {
  fromRevisionId: string | number
  toRevisionId: string | number
  routeId?: string
  directionId?: number
  serviceId?: string
  offset?: number
  limit?: number
  /** Set when comparing against a draft: the result is then never cached. */
  draftId?: string | number
}

/** GET /revision-comparisons — the schedule diff between two revisions (one page of trip changes per `offset`/`limit`). Drafts are never cached. */
export const compareRevisionsQuery = defineQueryOptions((p: CompareRevisionsParams) => {
  const { draftId, ...filters } = p
  return {
    key: compareKeys.compare(filters, draftId),
    query: async ({ signal }): Promise<ScheduleComparison> =>
      (await Schedule.compareRevisions({
        query: {
          fromRevisionId: String(p.fromRevisionId),
          toRevisionId: String(p.toRevisionId),
          routeId: p.routeId,
          directionId: p.directionId,
          serviceId: p.serviceId,
          offset: p.offset,
          limit: p.limit,
        },
        signal,
      })).data,
    ...(draftId !== undefined && draftId !== null && draftId !== '' ? { staleTime: 0, gcTime: 0 } : {}),
  }
})

type FeedGeometry = Awaited<ReturnType<typeof Schedule.feedGeometry>>['data']

/** GET /feeds/{feedCode}/geometry — the network GeoJSON FeatureCollection. Fresh for 60 s unless pinned (immutable) or a draft (never cached). */
export const feedGeometryQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'geometry'),
  query: async ({ signal }): Promise<FeedGeometry> =>
    (await Schedule.feedGeometry({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p) }, signal })).data,
  staleTime: 60_000,
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/schedule-times — every ScheduleTime, optionally of one trip or pattern (all pages). */
export const scheduleTimesQuery = defineQueryOptions((p: Rev & { tripId?: string, patternKey?: string }) => ({
  key: feedKeys.resource(p, 'schedule-times', { tripId: p.tripId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<ScheduleTime[]> =>
    fetchAllPages((offset, limit) => Schedule.scheduleTimes({ path: { feedCode: p.feedCode }, query: { tripId: p.tripId, patternKey: p.patternKey, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/stop-paths — every StopPath, optionally of one route or pattern (all pages). Fresh for 60 s on the active revision. */
export const stopPathsQuery = defineQueryOptions((p: Rev & { routeId?: string, patternKey?: string }) => ({
  key: feedKeys.resource(p, 'stop-paths', { routeId: p.routeId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<StopPath[]> =>
    fetchAllPages((offset, limit) => Schedule.stopPaths({ path: { feedCode: p.feedCode }, query: { routeId: p.routeId, patternKey: p.patternKey, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))

/** GET /feeds/{feedCode}/stops/{stopId}/schedule — which patterns call at a stop, with departures and service days. */
export const stopScheduleQuery = defineQueryOptions((p: Rev & { stopId: string }) => ({
  key: feedKeys.resource(p, 'stop-schedule', { stopId: p.stopId }),
  query: async ({ signal }): Promise<StopSchedule> =>
    (await Schedule.stopSchedule({ path: { feedCode: p.feedCode, stopId: p.stopId }, query: { revisionId: revisionParam(p) }, signal })).data,
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/trip-patterns/{patternKey} — one TripPattern, or null when unknown. */
export const tripPatternQuery = defineQueryOptions((p: Rev & { patternKey: string }) => ({
  key: feedKeys.resource(p, 'trip-patterns', { patternKey: p.patternKey }),
  query: ({ signal }): Promise<TripPattern | null> =>
    optional(Schedule.tripPattern({ path: { feedCode: p.feedCode, patternKey: p.patternKey }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/trip-patterns — every TripPattern, optionally of one route (all pages). */
export const tripPatternsQuery = defineQueryOptions((p: Rev & { routeId?: string }) => ({
  key: feedKeys.resource(p, 'trip-patterns', { routeId: p.routeId }),
  query: ({ signal }): Promise<TripPattern[]> =>
    fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode: p.feedCode }, query: { routeId: p.routeId, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
