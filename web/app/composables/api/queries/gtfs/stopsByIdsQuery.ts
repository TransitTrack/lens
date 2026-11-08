import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Stop } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** Ids per request, keeping the repeated `?stopId=` query well under URL length limits. */
const IDS_PER_REQUEST = 100

/**
 * GET /feeds/{feedCode}/stops?stopId=… — only the given stops (ids de-duplicated and sorted, 100 per request, all pages
 * of each). An empty id list resolves to `[]` without a request. Fresh for 60 s on the active revision.
 */
export const stopsByIdsQuery = defineQueryOptions((p: Rev & { stopIds: string[] }) => {
  const stopIds = [...new Set(p.stopIds.filter(Boolean))].sort()
  return {
    key: feedKeys.resource(p, 'stops', { stopIds }),
    query: async ({ signal }): Promise<Stop[]> => {
      const chunks: string[][] = []
      for (let i = 0; i < stopIds.length; i += IDS_PER_REQUEST) chunks.push(stopIds.slice(i, i + IDS_PER_REQUEST))
      const pages = await Promise.all(chunks.map((stopId) =>
        fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), stopId, offset, limit }, signal }))))
      return pages.flat()
    },
    ...cachePolicy(p, 'activeList'),
  }
})
