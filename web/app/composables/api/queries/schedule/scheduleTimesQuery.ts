import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type ScheduleTime } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/schedule-times — every ScheduleTime, optionally of one trip or pattern (all pages). */
export const scheduleTimesQuery = defineQueryOptions((p: Rev & { tripId?: string, patternKey?: string }) => ({
  key: feedKeys.resource(p, 'schedule-times', { tripId: p.tripId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<ScheduleTime[]> =>
    fetchAllPages((offset, limit) => Schedule.scheduleTimes({ path: { feedCode: p.feedCode }, query: { tripId: p.tripId, patternKey: p.patternKey, revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
