import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type FeedInfo } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/feed-info — one FeedInfo, or null when unknown. */
export const feedInfoQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'feed-info'),
  query: ({ signal }): Promise<FeedInfo | null> =>
    optional(Gtfs.feedInfo({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
