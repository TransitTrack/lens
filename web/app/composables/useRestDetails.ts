import { Gtfs, Schedule, optional } from '~/api'
import type {
  BlockDetailQuery, BlockDetailQueryVariables, BlocksListQuery, BlocksListQueryVariables,
  FeedDetailQuery, FeedDetailQueryVariables, FeedReadinessQuery, FeedReadinessQueryVariables,
  RevisionsQuery, RevisionsQueryVariables, RouteDetailQuery, RouteDetailQueryVariables,
  StopDetailQuery, StopDetailQueryVariables, StopScheduleQuery, StopScheduleQueryVariables, TripDetailQuery, TripDetailQueryVariables,
  ValidationFindingsQuery, ValidationFindingsQueryVariables,
  ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { fetchAllPages } from '~/utils/restPagination'
import {
  blockWithTrips, shapeOf, stopPathsByPattern, stopsByIds,
} from '~/utils/restRelations'

/** A feed with its revisions (newest first) and the active one picked out. */
async function feedWithRevisions(code: string, signal: AbortSignal) {
  const feed = await optional(Gtfs.feed({ path: { code }, signal }))
  if (!feed) return null
  const revisions = await fetchAllPages((offset, limit) => Gtfs.revisions({ path: { feedCode: code }, query: { offset, limit }, signal }))
  return { ...feed, revisions, activeRevision: revisions.find((r) => r.status === 'ACTIVE') ?? null }
}

export function useFeedDetailQuery(options: () => RestQueryOptions<FeedDetailQueryVariables>) {
  return useRestQuery('useFeedDetailQuery', options, async ({ code }, signal): Promise<FeedDetailQuery> => {
    const [feed, feedInfo] = await Promise.all([
      feedWithRevisions(code, signal),
      optional(Gtfs.feedInfo({ path: { feedCode: code }, signal })),
    ])
    return { feed, feedInfo } as unknown as FeedDetailQuery
  })
}

export function useFeedReadinessQuery(options: () => RestQueryOptions<FeedReadinessQueryVariables>) {
  return useRestQuery('useFeedReadinessQuery', options, async ({ code }, signal): Promise<FeedReadinessQuery> => ({
    feed: await feedWithRevisions(code, signal) as FeedReadinessQuery['feed'],
  }))
}

export function useRouteDetailQuery(options: () => RestQueryOptions<RouteDetailQueryVariables>) {
  return useRestQuery('useRouteDetailQuery', options, async (v, signal): Promise<RouteDetailQuery> => {
    const [route, patterns, stopPaths] = await Promise.all([
      optional(Gtfs.route({ path: { feedCode: v.feedCode, routeId: v.routeId }, signal })),
      fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode: v.feedCode }, query: { routeId: v.routeId, offset, limit }, signal })),
      stopPathsByPattern(v, signal),
    ])
    const tripPatterns = patterns.map((p) => ({ ...p, stopPaths: stopPaths.get(p.patternKey) ?? [] }))
    return { route, tripPatterns } as unknown as RouteDetailQuery
  })
}

export function useTripDetailQuery(options: () => RestQueryOptions<TripDetailQueryVariables>) {
  return useRestQuery('useTripDetailQuery', options, async (v, signal): Promise<TripDetailQuery> => {
    const trip = await optional(Gtfs.trip({ path: { feedCode: v.feedCode, tripId: v.tripId }, signal }))
    if (!trip) return { trip: null }
    const [route, pattern, block, scheduleTimes, stopTimes, shape] = await Promise.all([
      optional(Gtfs.route({ path: { feedCode: v.feedCode, routeId: trip.routeId }, signal })),
      trip.patternKey ? optional(Schedule.tripPattern({ path: { feedCode: v.feedCode, patternKey: trip.patternKey }, signal })) : null,
      trip.blockId ? optional(Schedule.block({ path: { feedCode: v.feedCode, blockId: trip.blockId }, query: { serviceId: trip.serviceId }, signal })) : null,
      fetchAllPages((offset, limit) => Schedule.scheduleTimes({ path: { feedCode: v.feedCode }, query: { tripId: trip.tripId, offset, limit }, signal })),
      fetchAllPages((offset, limit) => Gtfs.stopTimes({ path: { feedCode: v.feedCode, tripId: trip.tripId }, query: { offset, limit }, signal })),
      shapeOf({ feedCode: v.feedCode }, trip.shapeId, signal),
    ])
    const stops = await stopsByIds({ feedCode: v.feedCode }, stopTimes.map((st) => st.stopId), signal)
    return {
      trip: {
        ...trip,
        route,
        pattern,
        block,
        scheduleTimes,
        stopTimes: stopTimes.map((st) => ({ ...st, stop: st.stopId ? stops.get(st.stopId) ?? null : null })),
        shape,
      },
    } as unknown as TripDetailQuery
  })
}

export function useStopDetailQuery(options: () => RestQueryOptions<StopDetailQueryVariables>) {
  return useRestQuery('useStopDetailQuery', options, async (v, signal): Promise<StopDetailQuery> => ({
    stop: await optional(Gtfs.stop({ path: { feedCode: v.feedCode, stopId: v.stopId }, signal })) as StopDetailQuery['stop'],
  }))
}

/** Which patterns call at a stop, their scheduled departures there, and what days each service runs. */
export function useStopScheduleQuery(options: () => RestQueryOptions<StopScheduleQueryVariables>) {
  return useRestQuery('useStopScheduleQuery', options, async (v, signal): Promise<StopScheduleQuery> => ({
    stopSchedule: (await Schedule.stopSchedule({ path: { feedCode: v.feedCode, stopId: v.stopId }, signal })).data,
  }))
}

export function useBlockDetailQuery(options: () => RestQueryOptions<BlockDetailQueryVariables>) {
  return useRestQuery('useBlockDetailQuery', options, async (v, signal): Promise<BlockDetailQuery> => {
    const detail = await blockWithTrips(v, signal)
    return { block: detail ? { ...detail.block, blockTrips: detail.blockTrips } : null } as unknown as BlockDetailQuery
  })
}

export function useBlocksListQuery(options: () => RestQueryOptions<BlocksListQueryVariables>) {
  return useRestQuery('useBlocksListQuery', options, async (v, signal): Promise<BlocksListQuery> => {
    const [blocks, trips] = await Promise.all([
      fetchAllPages((offset, limit) => Schedule.blocks({ path: { feedCode: v.feedCode }, query: { offset, limit }, signal })),
      fetchAllPages((offset, limit) => Gtfs.trips({ path: { feedCode: v.feedCode }, query: { offset, limit }, signal })),
    ])
    const tripsByBlock = new Map<string, typeof trips>()
    for (const trip of trips) {
      if (!trip.blockId) continue
      const key = `${trip.serviceId}|${trip.blockId}`
      tripsByBlock.set(key, [...(tripsByBlock.get(key) ?? []), trip])
    }
    return { blocks: blocks.map((b) => ({ ...b, trips: tripsByBlock.get(`${b.serviceId}|${b.blockId}`) ?? [] })) } as unknown as BlocksListQuery
  })
}

export function useRevisionsQuery(options: () => RestQueryOptions<RevisionsQueryVariables>) {
  return useRestQuery('useRevisionsQuery', options, async (v, signal): Promise<RevisionsQuery> => ({
    revisions: await fetchAllPages((offset, limit) => Gtfs.revisions({ path: { feedCode: v.feedCode }, query: { offset, limit }, signal })) as RevisionsQuery['revisions'],
  }))
}

export function useValidationFindingsQuery(options: () => RestQueryOptions<ValidationFindingsQueryVariables>) {
  return useRestQuery('useValidationFindingsQuery', options, async (v, signal): Promise<ValidationFindingsQuery> => ({
    validationFindings: await fetchAllPages((offset, limit) => Gtfs.validationFindings({
      path: { feedCode: v.feedCode },
      query: { entityId: v.entityId ?? undefined, entityType: v.entityType ?? undefined, offset, limit },
      signal,
    })) as ValidationFindingsQuery['validationFindings'],
  }))
}

export function useValidationFindingCountsQuery(options: () => RestQueryOptions<ValidationFindingCountsQueryVariables>) {
  return useRestQuery('useValidationFindingCountsQuery', options, async (v, signal): Promise<ValidationFindingCountsQuery> => ({
    validationFindingCounts: await fetchAllPages((offset, limit) => Gtfs.validationFindingCounts({ path: { feedCode: v.feedCode }, query: { entityType: v.entityType, offset, limit }, signal })) as ValidationFindingCountsQuery['validationFindingCounts'],
  }))
}
