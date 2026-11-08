import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Route } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** Ids per request, keeping the repeated `?routeId=` query well under URL length limits. */
const IDS_PER_REQUEST = 100

/**
 * GET /feeds/{feedCode}/routes?routeId=… — only the given routes (ids de-duplicated and sorted, 100 per request, all pages
 * of each). An empty id list resolves to `[]` without a request. Fresh for 60 s on the active revision.
 */
export const routesByIdsQuery = defineQueryOptions((p: Rev & { routeIds: string[] }) => {
  const routeIds = [...new Set(p.routeIds.filter(Boolean))].sort()
  return {
    key: feedKeys.resource(p, 'routes', { routeIds }),
    query: async ({ signal }): Promise<Route[]> => {
      const chunks: string[][] = []
      for (let i = 0; i < routeIds.length; i += IDS_PER_REQUEST) chunks.push(routeIds.slice(i, i + IDS_PER_REQUEST))
      const pages = await Promise.all(chunks.map((routeId) =>
        fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), routeId, offset, limit }, signal }))))
      return pages.flat()
    },
    ...cachePolicy(p, 'activeList'),
  }
})
