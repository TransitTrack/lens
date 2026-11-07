import { GTFSApi, ScheduleApi, ResponseError } from '~~/generated/rest'
import type {
  DraftsQuery, DraftsQueryVariables, DraftDetailQuery, DraftDetailQueryVariables,
  DraftEditsQuery, DraftEditsQueryVariables, DraftJobQuery, DraftJobQueryVariables,
  DraftGridQuery, DraftGridQueryVariables, DraftPatternsQuery, DraftPatternsQueryVariables,
  DraftTripDetailQuery, DraftTripDetailQueryVariables,
} from '~/types/restViews'
import { fetchAllPages } from '~/utils/restPagination'
import { notFoundAsNull } from '~/utils/restRelations'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

const gtfs = new GTFSApi()
const schedule = new ScheduleApi()
async function optional<T>(load: Promise<T>): Promise<T | null> {
  try { return await load } catch (error) {
    if (error instanceof ResponseError && error.response.status === 404) return null
    throw error
  }
}

export function useDraftsQuery(options: () => RestQueryOptions<DraftsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftsQuery> => ({
    drafts: await fetchAllPages((offset, limit) => gtfs.draftsRaw({ feedCode: v.feedCode ?? undefined, offset, limit }, { signal })) as DraftsQuery['drafts'],
  }))
}

export function useDraftDetailQuery(options: () => RestQueryOptions<DraftDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftDetailQuery> => ({
    draft: await optional(gtfs.draft({ id: String(v.id) }, { signal })) as DraftDetailQuery['draft'],
  }))
}

export function useDraftEditsQuery(options: () => RestQueryOptions<DraftEditsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftEditsQuery> => ({
    draftEdits: await gtfs.draftEdits({ id: String(v.id), limit: v.limit ?? undefined }, { signal }) as DraftEditsQuery['draftEdits'],
  }))
}

export function useDraftJobQuery(options: () => RestQueryOptions<DraftJobQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftJobQuery> => ({
    draftJob: await optional(gtfs.draftJob({ jobId: String(v.jobId) }, { signal })) as DraftJobQuery['draftJob'],
  }))
}

export function useDraftGridQuery(options: () => RestQueryOptions<DraftGridQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftGridQuery> => ({
    draftGrid: await gtfs.draftGrid({ ...v, draftId: String(v.draftId), directionId: v.directionId ?? undefined, serviceId: v.serviceId ?? undefined }, { signal }) as DraftGridQuery['draftGrid'],
  }))
}

async function draftPatterns(v: DraftPatternsQueryVariables, signal: AbortSignal): Promise<DraftPatternsQuery['tripPatterns']> {
  const revisionId = String(v.revisionId)
  const [patterns, stopPaths] = await Promise.all([
    fetchAllPages((offset, limit) => schedule.tripPatternsRaw({ ...v, revisionId, offset, limit }, { signal })),
    fetchAllPages((offset, limit) => schedule.stopPathsRaw({ feedCode: v.feedCode, routeId: v.routeId, revisionId, offset, limit }, { signal })),
  ])
  return patterns.map((p) => ({ ...p, stopPaths: stopPaths.filter((sp) => sp.patternKey === p.patternKey) })) as DraftPatternsQuery['tripPatterns']
}

export function useDraftPatternsQuery(options: () => RestQueryOptions<DraftPatternsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftPatternsQuery> => ({
    tripPatterns: await draftPatterns(v, signal),
  }))
}

export function useDraftTripDetailQuery(options: () => RestQueryOptions<DraftTripDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<DraftTripDetailQuery> => {
    const revisionId = String(v.revisionId)
    const trip = await optional(gtfs.trip({ ...v, revisionId }, { signal }))
    if (!trip) return { trip: null }
    const [pattern, block] = await Promise.all([
      trip.patternKey ? schedule.tripPattern({ feedCode: v.feedCode, patternKey: trip.patternKey, revisionId }, { signal }).catch(notFoundAsNull) : null,
      trip.blockId ? schedule.block({ feedCode: v.feedCode, blockId: trip.blockId, serviceId: trip.serviceId, revisionId }, { signal }).catch(notFoundAsNull) : null,
    ])
    return { trip: { ...trip, pattern, block } } as unknown as DraftTripDetailQuery
  })
}
