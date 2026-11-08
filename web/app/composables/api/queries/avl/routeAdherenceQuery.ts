import { defineQueryOptions } from '@pinia/colada'
import { Reports, type StopPassage } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/routes/{routeId}/adherence — a route's stop passages in a date range (all pages). */
export const routeAdherenceQuery = defineQueryOptions((p: { feedCode: string, routeId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'route-adherence', { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.routeAdherence({
      path: { feedCode: p.feedCode, routeId: p.routeId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))
