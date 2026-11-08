import { defineQueryOptions } from '@pinia/colada'
import { Reports, type TripAdherenceSummary } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/reports/trip-adherence — per-trip adherence summaries in a date range, optionally for one route (all pages). */
export const allTripsAdherenceQuery = defineQueryOptions((p: { feedCode: string, routeId?: string, startDate: string, endDate: string }) => ({
  key: avlKeys.resource(p.feedCode, 'all-trips-adherence', { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate }),
  query: ({ signal }): Promise<TripAdherenceSummary[]> =>
    fetchAllPages((offset, limit) => Reports.allTripsAdherence({
      path: { feedCode: p.feedCode },
      query: { routeId: p.routeId, startDate: p.startDate, endDate: p.endDate, offset, limit },
      signal,
    })),
}))
