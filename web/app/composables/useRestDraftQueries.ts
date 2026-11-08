import { Gtfs, Schedule, optional } from '~/api'
import type {
  DraftsQuery, DraftsQueryVariables, DraftDetailQuery, DraftDetailQueryVariables,
  DraftEditsQuery, DraftEditsQueryVariables, DraftJobQuery, DraftJobQueryVariables,
  DraftGridQuery, DraftGridQueryVariables, DraftPatternsQuery, DraftPatternsQueryVariables,
  DraftTripDetailQuery, DraftTripDetailQueryVariables,
} from '~/types/restViews'
import { fetchAllPages } from '~/utils/restPagination'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

/** Drafts under edit must never be served from a stale cache entry: drop the data as soon as nothing observes it. */
const useDraftRead = <T, V>(key: string, options: () => RestQueryOptions<V>, load: (variables: V, signal: AbortSignal) => Promise<T>) =>
  useRestQuery(key, options, load, { gcTime: 0 })

export function useDraftsQuery(options: () => RestQueryOptions<DraftsQueryVariables>) {
  return useDraftRead('useDraftsQuery', options, async (v, signal): Promise<DraftsQuery> => ({
    drafts: await fetchAllPages((offset, limit) => Gtfs.drafts({ query: { feedCode: v.feedCode ?? undefined, offset, limit }, signal })) as DraftsQuery['drafts'],
  }))
}

export function useDraftDetailQuery(options: () => RestQueryOptions<DraftDetailQueryVariables>) {
  return useDraftRead('useDraftDetailQuery', options, async (v, signal): Promise<DraftDetailQuery> => ({
    draft: await optional(Gtfs.draft({ path: { id: String(v.id) }, signal })) as DraftDetailQuery['draft'],
  }))
}

export function useDraftEditsQuery(options: () => RestQueryOptions<DraftEditsQueryVariables>) {
  return useDraftRead('useDraftEditsQuery', options, async (v, signal): Promise<DraftEditsQuery> => ({
    draftEdits: (await Gtfs.draftEdits({ path: { id: String(v.id) }, query: { limit: v.limit ?? undefined }, signal })).data as DraftEditsQuery['draftEdits'],
  }))
}

export function useDraftJobQuery(options: () => RestQueryOptions<DraftJobQueryVariables>) {
  return useDraftRead('useDraftJobQuery', options, async (v, signal): Promise<DraftJobQuery> => ({
    draftJob: await optional(Gtfs.draftJob({ path: { jobId: String(v.jobId) }, signal })) as DraftJobQuery['draftJob'],
  }))
}

export function useDraftGridQuery(options: () => RestQueryOptions<DraftGridQueryVariables>) {
  return useDraftRead('useDraftGridQuery', options, async (v, signal): Promise<DraftGridQuery> => ({
    draftGrid: (await Gtfs.draftGrid({
      path: { draftId: String(v.draftId) },
      query: { routeId: v.routeId, directionId: v.directionId ?? undefined, serviceId: v.serviceId ?? undefined },
      signal,
    })).data as DraftGridQuery['draftGrid'],
  }))
}

async function draftPatterns(v: DraftPatternsQueryVariables, signal: AbortSignal): Promise<DraftPatternsQuery['tripPatterns']> {
  const revisionId = String(v.revisionId)
  const [patterns, stopPaths] = await Promise.all([
    fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode: v.feedCode }, query: { routeId: v.routeId, revisionId, offset, limit }, signal })),
    fetchAllPages((offset, limit) => Schedule.stopPaths({ path: { feedCode: v.feedCode }, query: { routeId: v.routeId, revisionId, offset, limit }, signal })),
  ])
  return patterns.map((p) => ({ ...p, stopPaths: stopPaths.filter((sp) => sp.patternKey === p.patternKey) })) as DraftPatternsQuery['tripPatterns']
}

export function useDraftPatternsQuery(options: () => RestQueryOptions<DraftPatternsQueryVariables>) {
  return useDraftRead('useDraftPatternsQuery', options, async (v, signal): Promise<DraftPatternsQuery> => ({
    tripPatterns: await draftPatterns(v, signal),
  }))
}

export function useDraftTripDetailQuery(options: () => RestQueryOptions<DraftTripDetailQueryVariables>) {
  return useDraftRead('useDraftTripDetailQuery', options, async (v, signal): Promise<DraftTripDetailQuery> => {
    const revisionId = String(v.revisionId)
    const trip = await optional(Gtfs.trip({ path: { feedCode: v.feedCode, tripId: v.tripId }, query: { revisionId }, signal }))
    if (!trip) return { trip: null }
    const [pattern, block] = await Promise.all([
      trip.patternKey ? optional(Schedule.tripPattern({ path: { feedCode: v.feedCode, patternKey: trip.patternKey }, query: { revisionId }, signal })) : null,
      trip.blockId ? optional(Schedule.block({ path: { feedCode: v.feedCode, blockId: trip.blockId }, query: { serviceId: trip.serviceId, revisionId }, signal })) : null,
    ])
    return { trip: { ...trip, pattern, block } } as unknown as DraftTripDetailQuery
  })
}
