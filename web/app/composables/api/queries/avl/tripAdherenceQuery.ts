import { defineQueryOptions } from '@pinia/colada'
import { Reports, type StopPassage } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/trips/{tripId}/adherence — a trip's stop passages in a date range (all pages). */
export const tripAdherenceQuery = defineQueryOptions((p: { feedCode: string, tripId: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'trip-adherence', { tripId: p.tripId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<StopPassage[]> =>
    fetchAllPages((offset, limit) => Reports.tripAdherence({
      path: { feedCode: p.feedCode, tripId: p.tripId },
      query: { startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))
