import { defineQueryOptions } from '@pinia/colada'
import { Avl, type AvlReport } from '~/api'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/reports — a vehicle's newest raw AVL reports (one response). */
export const avlReportsQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, limit?: number, since?: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'reports', { vehicleId: p.vehicleId, limit: p.limit, since: p.since }),
  query: async ({ signal }): Promise<AvlReport[]> =>
    (await Avl.avlReports({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { limit: p.limit, since: p.since }, signal })).data,
}))
