import { defineQueryOptions } from '@pinia/colada'
import { Schedule } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

type FeedGeometry = Awaited<ReturnType<typeof Schedule.feedGeometry>>['data']

/** GET /feeds/{feedCode}/geometry — the network GeoJSON FeatureCollection. Fresh for 60 s unless pinned (immutable) or a draft (never cached). */
export const feedGeometryQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'geometry'),
  query: async ({ signal }): Promise<FeedGeometry> =>
    (await Schedule.feedGeometry({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p) }, signal })).data,
  staleTime: 60_000,
  ...cachePolicy(p),
}))
