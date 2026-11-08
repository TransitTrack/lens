import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type EntityFindingCount } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/validation-finding-counts — every EntityFindingCount row (all pages). */
export const validationFindingCountsQuery = defineQueryOptions((p: Rev & { entityType: string }) => ({
  key: feedKeys.resource(p, 'validation-finding-counts', { entityType: p.entityType }),
  query: ({ signal }): Promise<EntityFindingCount[]> =>
    fetchAllPages((offset, limit) => Gtfs.validationFindingCounts({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), entityType: p.entityType, offset, limit }, signal })),
  ...cachePolicy(p),
}))
