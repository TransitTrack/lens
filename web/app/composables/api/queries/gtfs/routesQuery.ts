import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Route } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/routes — every Route row (all pages). Fresh for 60 s on the active revision. */
export const routesQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'routes'),
  query: ({ signal }): Promise<Route[]> =>
    fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))
