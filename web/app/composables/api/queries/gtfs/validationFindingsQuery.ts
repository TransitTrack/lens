import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type ValidationFinding } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/validation-findings — every ValidationFinding row (all pages). */
export const validationFindingsQuery = defineQueryOptions((p: Rev & { entityType?: string; entityId?: string }) => ({
  key: feedKeys.resource(p, 'validation-findings', { entityType: p.entityType, entityId: p.entityId }),
  query: ({ signal }): Promise<ValidationFinding[]> =>
    fetchAllPages((offset, limit) => Gtfs.validationFindings({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), entityType: p.entityType, entityId: p.entityId, offset, limit }, signal })),
  ...cachePolicy(p),
}))
