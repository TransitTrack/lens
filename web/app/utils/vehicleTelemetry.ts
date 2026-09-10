interface LatLonLike {
  lat: number | null | undefined
  lon: number | null | undefined
}

/** Great-circle distance between two points, in metres. `null` if either is incomplete. */
export function haversineM(
  a: LatLonLike | null | undefined,
  b: LatLonLike | null | undefined,
): number | null {
  if (a?.lat == null || a?.lon == null || b?.lat == null || b?.lon == null) return null
  const R = 6371000
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLon = toRad(b.lon - a.lon)
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)))
}

/** Mean seconds between consecutive AVL reports. `null` with fewer than two. */
export function avgIntervalSec(reports: { ts: string }[]): number | null {
  const times = reports
    .map((r) => Date.parse(r.ts))
    .filter((t) => Number.isFinite(t))
    .sort((x, y) => x - y)
  if (times.length < 2) return null
  const span = times[times.length - 1]! - times[0]!
  return span / (times.length - 1) / 1000
}

interface BlockTripLike {
  listIndex: number
  trip: { tripId: string }
}

/** Where `tripId` sits in a block's trip list, 1-based. `null` if not found. */
export function blockTripProgress(
  blockTrips: BlockTripLike[] | null | undefined,
  tripId: string | null | undefined,
): { index: number; total: number } | null {
  if (!blockTrips?.length || !tripId) return null
  const ordered = [...blockTrips].sort((a, b) => a.listIndex - b.listIndex)
  const at = ordered.findIndex((bt) => bt.trip.tripId === tripId)
  return at === -1 ? null : { index: at + 1, total: ordered.length }
}

const STATUS_LABELS: Record<string, string> = {
  INCOMING_AT: 'Approaching stop',
  STOPPED_AT: 'Stopped at stop',
  IN_TRANSIT_TO: 'In transit',
}

/** Human label for a GTFS-realtime `VehicleStopStatus`. */
export function currentStatusLabel(raw: string | null | undefined): string | null {
  if (!raw) return null
  return STATUS_LABELS[raw] ?? raw
}
