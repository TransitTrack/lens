import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type TripPattern } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/trip-patterns — every TripPattern, optionally of one route (all pages). */
export const tripPatternsQuery = defineQueryOptions((p: Rev & { routeId?: string }) => ({
  key: feedKeys.resource(p, 'trip-patterns', { routeId: p.routeId }),
  query: ({ signal }): Promise<TripPattern[]> =>
    fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode: p.feedCode }, query: { routeId: p.routeId, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
