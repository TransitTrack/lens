import { defineQueryOptions } from '@pinia/colada'
import { Schedule, optional, type TripPattern } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/trip-patterns/{patternKey} — one TripPattern, or null when unknown. */
export const tripPatternQuery = defineQueryOptions((p: Rev & { patternKey: string }) => ({
  key: feedKeys.resource(p, 'trip-patterns', { patternKey: p.patternKey }),
  query: ({ signal }): Promise<TripPattern | null> =>
    optional(Schedule.tripPattern({ path: { feedCode: p.feedCode, patternKey: p.patternKey }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))
