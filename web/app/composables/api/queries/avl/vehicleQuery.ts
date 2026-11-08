import { defineQueryOptions } from '@pinia/colada'
import { Avl, optional, type Vehicle } from '~/api'
import { avlKeys } from '../../keys/avl'

/** GET /feeds/{feedCode}/avl/vehicles/{vehicleId} — one live vehicle, or null when unknown. */
export const vehicleQuery = defineQueryOptions((p: { avlFeedCode: string, vehicleId: string }) => ({
  key: avlKeys.resource(p.avlFeedCode, 'vehicles', { vehicleId: p.vehicleId }),
  query: ({ signal }): Promise<Vehicle | null> =>
    optional(Avl.vehicle({ path: { feedCode: p.avlFeedCode, vehicleId: p.vehicleId }, signal })),
}))
