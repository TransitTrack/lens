import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Agency } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/agencies — every Agency row (all pages). */
export const agenciesQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'agencies'),
  query: ({ signal }): Promise<Agency[]> =>
    fetchAllPages((offset, limit) => Gtfs.agencies({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
