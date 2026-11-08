import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Stop } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/stops — every Stop row (all pages). Fresh for 60 s on the active revision. */
export const stopsQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'stops'),
  query: ({ signal }): Promise<Stop[]> =>
    fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))
