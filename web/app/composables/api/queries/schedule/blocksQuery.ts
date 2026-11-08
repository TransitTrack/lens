import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type Block } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/blocks — every Block of the revision (all pages). */
export const blocksQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'blocks'),
  query: ({ signal }): Promise<Block[]> =>
    fetchAllPages((offset, limit) => Schedule.blocks({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
