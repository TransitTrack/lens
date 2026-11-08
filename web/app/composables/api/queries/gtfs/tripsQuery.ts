import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Trip } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/trips — every Trip row (all pages). */
export const tripsQuery = defineQueryOptions((p: Rev & { routeId?: string; serviceId?: string; blockId?: string; patternKey?: string }) => ({
  key: feedKeys.resource(p, 'trips', { routeId: p.routeId, serviceId: p.serviceId, blockId: p.blockId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<Trip[]> =>
    fetchAllPages((offset, limit) => Gtfs.trips({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), routeId: p.routeId, serviceId: p.serviceId, blockId: p.blockId, patternKey: p.patternKey, offset, limit }, signal })),
  ...cachePolicy(p),
}))
