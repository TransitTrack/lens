import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type BlockTrip } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/block-trips — every BlockTrip, optionally of one block / service (all pages). */
export const blockTripsQuery = defineQueryOptions((p: Rev & { blockId?: string, serviceId?: string }) => ({
  key: feedKeys.resource(p, 'block-trips', { blockId: p.blockId, serviceId: p.serviceId }),
  query: ({ signal }): Promise<BlockTrip[]> =>
    fetchAllPages((offset, limit) => Schedule.blockTrips({ path: { feedCode: p.feedCode }, query: { blockId: p.blockId, serviceId: p.serviceId, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
