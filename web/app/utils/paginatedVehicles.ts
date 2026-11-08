import type { VehicleRow } from '~/composables/useVehiclePolling'

/**
 * Fetch the feed's live vehicles from the map endpoint, which returns every vehicle in one response
 * with just the trip / route / block / pattern fields the map and board draw.
 */
export async function fetchAllVehicles(feedCode: string, signal: AbortSignal, fetcher: typeof fetch = fetch): Promise<VehicleRow[]> {
  const response = await fetcher(`/api/v1/feeds/${encodeURIComponent(feedCode)}/vehicles`, { signal })
  if (!response.ok) throw new Error(`Vehicle request failed: ${response.status}`)
  const rows: unknown = await response.json()
  if (!Array.isArray(rows)) throw new Error('Invalid vehicle response')
  return rows as VehicleRow[]
}
