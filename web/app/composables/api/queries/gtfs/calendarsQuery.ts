import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type Calendar } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/calendars — every Calendar row (all pages). */
export const calendarsQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'calendars'),
  query: ({ signal }): Promise<Calendar[]> =>
    fetchAllPages((offset, limit) => Gtfs.calendars({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))
