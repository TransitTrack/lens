import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Trip } from '~/api'
import { feedKeys } from '../../keys/feeds'

/**
 * GET /feeds/{feedCode}/trips/{tripId}?revisionId=… — one request per distinct (revision, trip) key (de-duplicated and
 * sorted); a trip that no longer exists resolves to `null`. The result is keyed `${revisionId}|${tripId}`, the format
 * `reportsWithTrips` expects. An empty key list resolves to an empty map without a request. Revision-pinned, so it never goes stale.
 */
export const tripsByKeysQuery = defineQueryOptions((p: { feedCode: string, keys: Array<{ revisionId: string, tripId: string }> }) => {
  const keys = [...new Set(p.keys.map((k) => `${k.revisionId}|${k.tripId}`))].sort()
  return {
    key: feedKeys.resource({ feedCode: p.feedCode }, 'trips-by-keys', { keys }),
    query: async ({ signal }): Promise<Map<string, Trip | null>> => {
      const entries = await Promise.all(keys.map(async (key) => {
        const sep = key.indexOf('|')
        const revisionId = key.slice(0, sep)
        const tripId = key.slice(sep + 1)
        return [key, await optional(Gtfs.trip({ path: { feedCode: p.feedCode, tripId }, query: { revisionId }, signal }))] as const
      }))
      return new Map(entries)
    },
    staleTime: Infinity,
  }
})
