import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Stop } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/stops/{stopId} — one Stop, or null when unknown. */
export const stopQuery = defineQueryOptions((p: Rev & { stopId: string }) => ({
  key: feedKeys.resource(p, 'stops', { stopId: p.stopId }),
  query: ({ signal }): Promise<Stop | null> =>
    optional(Gtfs.stop({ path: { feedCode: p.feedCode, stopId: p.stopId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
