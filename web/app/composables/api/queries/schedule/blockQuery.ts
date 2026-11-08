import { defineQueryOptions } from '@pinia/colada'
import { Schedule, optional, type Block } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/blocks/{blockId} — one Block of a service, or null when unknown. */
export const blockQuery = defineQueryOptions((p: Rev & { blockId: string, serviceId: string }) => ({
  key: feedKeys.resource(p, 'blocks', { blockId: p.blockId, serviceId: p.serviceId }),
  query: ({ signal }): Promise<Block | null> =>
    optional(Schedule.block({ path: { feedCode: p.feedCode, blockId: p.blockId }, query: { serviceId: p.serviceId, revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
