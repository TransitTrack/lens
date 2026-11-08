import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type StopTime } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/trips/{tripId}/stop-times — every StopTime row (all pages). */
export const stopTimesQuery = defineQueryOptions((p: Rev & { tripId: string }) => ({
  key: feedKeys.resource(p, 'stop-times', { tripId: p.tripId }),
  query: ({ signal }): Promise<StopTime[]> =>
    fetchAllPages((offset, limit) => Gtfs.stopTimes({ path: { feedCode: p.feedCode, tripId: p.tripId }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
