import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Route } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/routes/{routeId} — one Route, or null when unknown. */
export const routeQuery = defineQueryOptions((p: Rev & { routeId: string }) => ({
  key: feedKeys.resource(p, 'routes', { routeId: p.routeId }),
  query: ({ signal }): Promise<Route | null> =>
    optional(Gtfs.route({ path: { feedCode: p.feedCode, routeId: p.routeId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
