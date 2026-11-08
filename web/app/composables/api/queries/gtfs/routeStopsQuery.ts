import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Stop } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/routes/{routeId}/stops — every Stop row (all pages). Fresh for 60 s on the active revision. */
export const routeStopsQuery = defineQueryOptions((p: Rev & { routeId: string }) => ({
  key: feedKeys.resource(p, 'route-stops', { routeId: p.routeId }),
  query: ({ signal }): Promise<Stop[]> =>
    fetchAllPages((offset, limit) => Gtfs.routeStops({ path: { feedCode: p.feedCode, routeId: p.routeId }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))
