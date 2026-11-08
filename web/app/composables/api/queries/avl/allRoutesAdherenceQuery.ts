import { defineQueryOptions } from '@pinia/colada'
import { Reports, type RouteAdherenceSummary } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/reports/route-adherence — per-route adherence summaries in a date range (all pages). */
export const allRoutesAdherenceQuery = defineQueryOptions((p: { feedCode: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'all-routes-adherence', { startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<RouteAdherenceSummary[]> =>
    fetchAllPages((offset, limit) => Reports.allRoutesAdherence({
      path: { feedCode: p.feedCode },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))
