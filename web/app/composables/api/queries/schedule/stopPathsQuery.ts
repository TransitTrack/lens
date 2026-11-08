import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type StopPath } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/stop-paths — every StopPath, optionally of one route or pattern (all pages). Fresh for 60 s on the active revision. */
export const stopPathsQuery = defineQueryOptions((p: Rev & { routeId?: string, patternKey?: string }) => ({
  key: feedKeys.resource(p, 'stop-paths', { routeId: p.routeId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<StopPath[]> =>
    fetchAllPages((offset, limit) => Schedule.stopPaths({ path: { feedCode: p.feedCode }, query: { routeId: p.routeId, patternKey: p.patternKey, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))
