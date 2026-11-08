import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type ScheduleComparison } from '~/api'
import { compareKeys } from '../../keys/schedule'

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
