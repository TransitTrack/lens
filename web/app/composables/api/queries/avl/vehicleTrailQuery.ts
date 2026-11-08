import { defineQueryOptions } from '@pinia/colada'
import { Avl, type VehicleTrail } from '~/api'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId}/trail — a vehicle's recent movement, summarised by the backend. */
export const vehicleTrailQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string, limit?: number }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'trail', { vehicleId: p.vehicleId, limit: p.limit }),
  query: async ({ signal }): Promise<VehicleTrail> =>
    (await Avl.vehicleTrail({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, query: { limit: p.limit }, signal })).data,
}))
