import { defineQueryOptions } from '@pinia/colada'
import type { VehicleRow } from '~/types/vehicles'
import { avlKeys } from '../../keys/avl'

/**
 * GET /feeds/{feedCode}/vehicles — the map endpoint: every live vehicle in one response, with just the
 * trip / route / block / pattern fields the map and board draw. Raw fetch (not in the generated SDK).
 */
export const liveVehiclesQuery = defineQueryOptions((p: { feedCode: string }) => ({
  key: avlKeys.resource(p.feedCode, 'live-vehicles'),
  query: async ({ signal }): Promise<VehicleRow[]> => {
    const response = await fetch(`/api/v1/feeds/${encodeURIComponent(p.feedCode)}/vehicles`, { signal })
    if (!response.ok) throw new Error(`Vehicle request failed: ${response.status}`)
    const rows: unknown = await response.json()
    if (!Array.isArray(rows)) throw new Error('Invalid vehicle response')
    return rows as VehicleRow[]
  },
}))
