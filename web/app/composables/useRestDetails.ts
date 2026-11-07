import { GTFSApi, ScheduleApi, ResponseError } from '~~/generated/rest'
import type {
  BlockDetailQuery, BlockDetailQueryVariables, BlocksListQuery, BlocksListQueryVariables,
  FeedDetailQuery, FeedDetailQueryVariables, FeedReadinessQuery, FeedReadinessQueryVariables,
  RevisionsQuery, RevisionsQueryVariables, RouteDetailQuery, RouteDetailQueryVariables,
  StopDetailQuery, StopDetailQueryVariables, TripDetailQuery, TripDetailQueryVariables,
  ValidationFindingsQuery, ValidationFindingsQueryVariables,
  ValidationFindingCountsQuery, ValidationFindingCountsQueryVariables,
} from '~/types/restViews'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'
import { fetchAllPages } from '~/utils/restPagination'
import {
  blockWithTrips, shapeOf, stopPathsByPattern, stopsById,
} from '~/utils/restRelations'

const gtfs = new GTFSApi()
const schedule = new ScheduleApi()
async function optional<T>(load: Promise<T>): Promise<T | null> {
  try { return await load } catch (error) {
    if (error instanceof ResponseError && error.response.status === 404) return null
    throw error
  }
}

/** A feed with its revisions (newest first) and the active one picked out. */
async function feedWithRevisions(code: string, signal: AbortSignal) {
  const feed = await optional(gtfs.feed({ code }, { signal }))
  if (!feed) return null
  const revisions = await fetchAllPages((offset, limit) => gtfs.revisionsRaw({ feedCode: code, offset, limit }, { signal }))
  return { ...feed, revisions, activeRevision: revisions.find((r) => r.status === 'ACTIVE') ?? null }
}

export function useFeedDetailQuery(options: () => RestQueryOptions<FeedDetailQueryVariables>) {
  return useRestQuery(options, async ({ code }, signal): Promise<FeedDetailQuery> => {
    const [feed, feedInfo] = await Promise.all([
      feedWithRevisions(code, signal),
      optional(gtfs.feedInfo({ feedCode: code }, { signal })),
    ])
    return { feed, feedInfo } as unknown as FeedDetailQuery
  })
}

export function useFeedReadinessQuery(options: () => RestQueryOptions<FeedReadinessQueryVariables>) {
  return useRestQuery(options, async ({ code }, signal): Promise<FeedReadinessQuery> => ({
    feed: await feedWithRevisions(code, signal) as FeedReadinessQuery['feed'],
  }))
}

export function useRouteDetailQuery(options: () => RestQueryOptions<RouteDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<RouteDetailQuery> => {
    const [route, patterns, stopPaths] = await Promise.all([
      optional(gtfs.route(v, { signal })),
      fetchAllPages((offset, limit) => schedule.tripPatternsRaw({ ...v, offset, limit }, { signal })),
      stopPathsByPattern(v, signal),
    ])
    const tripPatterns = patterns.map((p) => ({ ...p, stopPaths: stopPaths.get(p.patternKey) ?? [] }))
    return { route, tripPatterns } as unknown as RouteDetailQuery
  })
}

export function useTripDetailQuery(options: () => RestQueryOptions<TripDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<TripDetailQuery> => {
    const trip = await optional(gtfs.trip(v, { signal }))
    if (!trip) return { trip: null }
    const [route, pattern, block, scheduleTimes, stopTimes, stops, shape] = await Promise.all([
      optional(gtfs.route({ feedCode: v.feedCode, routeId: trip.routeId }, { signal })),
      trip.patternKey ? optional(schedule.tripPattern({ feedCode: v.feedCode, patternKey: trip.patternKey }, { signal })) : null,
      trip.blockId ? optional(schedule.block({ feedCode: v.feedCode, blockId: trip.blockId, serviceId: trip.serviceId }, { signal })) : null,
      fetchAllPages((offset, limit) => schedule.scheduleTimesRaw({ feedCode: v.feedCode, tripId: trip.tripId, offset, limit }, { signal })),
      fetchAllPages((offset, limit) => gtfs.stopTimesRaw({ feedCode: v.feedCode, tripId: trip.tripId, offset, limit }, { signal })),
      stopsById({ feedCode: v.feedCode }, signal),
      shapeOf({ feedCode: v.feedCode }, trip.shapeId, signal),
    ])
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
  return useRestQuery(options, async (v, signal): Promise<StopDetailQuery> => ({
    stop: await optional(gtfs.stop(v, { signal })) as StopDetailQuery['stop'],
  }))
}

export function useBlockDetailQuery(options: () => RestQueryOptions<BlockDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<BlockDetailQuery> => {
    const detail = await blockWithTrips(v, signal)
    return { block: detail ? { ...detail.block, blockTrips: detail.blockTrips } : null } as unknown as BlockDetailQuery
  })
}

export function useBlocksListQuery(options: () => RestQueryOptions<BlocksListQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<BlocksListQuery> => {
    const [blocks, trips] = await Promise.all([
      fetchAllPages((offset, limit) => schedule.blocksRaw({ ...v, offset, limit }, { signal })),
      fetchAllPages((offset, limit) => gtfs.tripsRaw({ ...v, offset, limit }, { signal })),
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
  return useRestQuery(options, async (v, signal): Promise<RevisionsQuery> => ({
    revisions: await fetchAllPages((offset, limit) => gtfs.revisionsRaw({ ...v, offset, limit }, { signal })) as RevisionsQuery['revisions'],
  }))
}

export function useValidationFindingsQuery(options: () => RestQueryOptions<ValidationFindingsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<ValidationFindingsQuery> => ({
    validationFindings: await fetchAllPages((offset, limit) => gtfs.validationFindingsRaw({
      ...v, entityId: v.entityId ?? undefined, entityType: v.entityType ?? undefined, offset, limit,
    }, { signal })) as ValidationFindingsQuery['validationFindings'],
  }))
}

export function useValidationFindingCountsQuery(options: () => RestQueryOptions<ValidationFindingCountsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<ValidationFindingCountsQuery> => ({
    validationFindingCounts: await fetchAllPages((offset, limit) => gtfs.validationFindingCountsRaw({ ...v, offset, limit }, { signal })) as ValidationFindingCountsQuery['validationFindingCounts'],
  }))
}
