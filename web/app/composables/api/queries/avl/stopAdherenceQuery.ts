import { defineQueryOptions } from '@pinia/colada'
import { Reports, type StopPassage } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/stops/{stopId}/adherence — a stop's passages in a date range (all pages). */
export const stopAdherenceQuery = defineQueryOptions((p: { feedCode: string, stopId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'stop-adherence', { stopId: p.stopId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.stopAdherence({
      path: { feedCode: p.feedCode, stopId: p.stopId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))
