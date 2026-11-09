import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Trip } from '~/api'
import { feedKeys } from '../../keys/feeds'

/**
 * GET /feeds/{feedCode}/trips/{tripId}?revisionId=… — one request per distinct (revision, trip) key (de-duplicated and
 * sorted). The result is keyed `${revisionId}|${tripId}`, the format `reportsWithTrips` expects. A trip that no longer
 * exists (404) or whose read fails resolves to `null`, so one bad trip never drops the others. An empty key list
 * resolves to an empty map without a request. Revision-pinned, so it never goes stale.
 */
export const tripsByKeysQuery = defineQueryOptions((p: { feedCode: string, keys: Array<{ revisionId: string, tripId: string }> }) => {
  const keys = [...new Set(p.keys.map((k) => `${k.revisionId}|${k.tripId}`))].sort()
  return {
    // Under the feed, not its active-revision scope: every read here is pinned to an explicit revision.
    key: [...feedKeys.feed(p.feedCode), 'trips-by-keys', { keys }] as const,
    query: async ({ signal }): Promise<Map<string, Trip | null>> => {
      const results = await Promise.allSettled(keys.map((key) => {
        const sep = key.indexOf('|')
        const revisionId = key.slice(0, sep)
        const tripId = key.slice(sep + 1)
        return optional(Gtfs.trip({ path: { feedCode: p.feedCode, tripId }, query: { revisionId }, signal }))
      }))
      // A cancelled read must reject, not resolve to a map of nulls that would be cached.
      if (signal.aborted) throw signal.reason
      return new Map(keys.map((key, i) => {
        const result = results[i]!
        return [key, result.status === 'fulfilled' ? result.value : null] as const
      }))
    },
    staleTime: Infinity,
  }
})
