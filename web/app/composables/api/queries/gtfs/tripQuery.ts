import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, optional, type Trip } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/trips/{tripId} — one Trip, or null when unknown. */
export const tripQuery = defineQueryOptions((p: Rev & { tripId: string }) => ({
  key: feedKeys.resource(p, 'trips', { tripId: p.tripId }),
  query: ({ signal }): Promise<Trip | null> =>
    optional(Gtfs.trip({ path: { feedCode: p.feedCode, tripId: p.tripId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
