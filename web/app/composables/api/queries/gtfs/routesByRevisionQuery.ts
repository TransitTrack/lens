import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Route } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys } from '../../keys/feeds'

/** Ids per request, keeping the repeated `?routeId=` query well under URL length limits. */
const IDS_PER_REQUEST = 100

/**
 * GET /feeds/{feedCode}/routes?revisionId=…&routeId=… — the given routes of each revision, read pinned to that
 * revision (ids de-duplicated and sorted, 100 per request, all pages of each). Returns `Map<revisionId, Map<routeId, Route>>`
 * so routes of different revisions are never merged. Revisions without ids are skipped; none at all resolves to an
 * empty map without a request. Revision-pinned, so it never goes stale.
 */
export const routesByRevisionQuery = defineQueryOptions((p: { feedCode: string, routeIdsByRevision: Record<string, string[]> }) => {
  const revisions = Object.entries(p.routeIdsByRevision)
    .map(([revisionId, ids]) => [revisionId, [...new Set(ids.filter(Boolean))].sort()] as [string, string[]])
    .filter(([, ids]) => ids.length > 0)
    .sort(([a], [b]) => a.localeCompare(b))
  return {
    // Under the feed, not its active-revision scope: every read here is pinned to an explicit revision.
    key: [...feedKeys.feed(p.feedCode), 'routes-by-revision', { revisions }] as const,
    query: async ({ signal }): Promise<Map<string, Map<string, Route>>> => {
      const entries = await Promise.all(revisions.map(async ([revisionId, routeIds]) => {
        const chunks: string[][] = []
        for (let i = 0; i < routeIds.length; i += IDS_PER_REQUEST) chunks.push(routeIds.slice(i, i + IDS_PER_REQUEST))
        const pages = await Promise.all(chunks.map((routeId) =>
          fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId, routeId, offset, limit }, signal }))))
        return [revisionId, new Map(pages.flat().map((route) => [route.routeId, route]))] as const
      }))
      return new Map(entries)
    },
    staleTime: Infinity,
  }
})
