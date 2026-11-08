import { defineQueryOptions } from '@pinia/colada'
import { Gtfs, type CalendarDate } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../../keys/feeds'
import { cachePolicy, revisionParam } from '../cachePolicy'

/** GET /feeds/{feedCode}/calendar-dates — every CalendarDate row (all pages). */
export const calendarDatesQuery = defineQueryOptions((p: Rev & { serviceId?: string }) => ({
  key: feedKeys.resource(p, 'calendar-dates', { serviceId: p.serviceId }),
  query: ({ signal }): Promise<CalendarDate[]> =>
    fetchAllPages((offset, limit) => Gtfs.calendarDates({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), serviceId: p.serviceId, offset, limit }, signal })),
  ...cachePolicy(p),
}))
