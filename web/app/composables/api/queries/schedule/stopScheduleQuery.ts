import { defineQueryOptions } from '@pinia/colada'
import { Schedule, type StopSchedule } from '~/api'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/stops/{stopId}/schedule — which patterns call at a stop, with departures and service days. */
export const stopScheduleQuery = defineQueryOptions((p: Rev & { stopId: string }) => ({
  key: feedKeys.resource(p, 'stop-schedule', { stopId: p.stopId }),
  query: async ({ signal }): Promise<StopSchedule> =>
    (await Schedule.stopSchedule({ path: { feedCode: p.feedCode, stopId: p.stopId }, query: { revisionId: revisionParam(p) }, signal })).data,
  ...cachePolicy(p),
}))
