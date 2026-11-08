import { defineQueryOptions } from '@pinia/colada'
import { Avl, type AvlReportPage } from '~/api'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/report-page — ONE page of a vehicle's reports on purpose (server-paged by page / pageSize). */
export const avlReportPageQuery = defineQueryOptions((p: {
  avlFeedCode: string, vehicleId: string, date: string, startTime: string, endTime: string, page?: number, pageSize?: number
}) => ({
  key: avlKeys.resource(p.avlFeedCode, 'report-page', {
    vehicleId: p.vehicleId, date: p.date, startTime: p.startTime, endTime: p.endTime, page: p.page, pageSize: p.pageSize,
  }),
  query: async ({ signal }): Promise<AvlReportPage> =>
    (await Avl.avlReportPage({
      path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId },
      query: { date: p.date, startTime: p.startTime, endTime: p.endTime, page: p.page, pageSize: p.pageSize },
      signal,
    })).data,
}))
