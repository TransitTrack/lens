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

/** Approximate geodesic area of a longitude/latitude polygon, in square metres. */
export function polygonAreaM2(points: [number, number][]): number {
  if (points.length < 3) return 0
  const toRad = (d: number) => (d * Math.PI) / 180
  let sum = 0
  for (let index = 0; index < points.length; index++) {
    const [lonA, latA] = points[index]!
    const [lonB, latB] = points[(index + 1) % points.length]!
    sum += (toRad(lonB) - toRad(lonA)) * (2 + Math.sin(toRad(latA)) + Math.sin(toRad(latB)))
  }
  return Math.abs(sum) * (6_371_000 ** 2) / 2
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

type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

const KNOWN_STATUSES = new Set(['INCOMING_AT', 'STOPPED_AT', 'IN_TRANSIT_TO'])

/** Human label for a GTFS-realtime `VehicleStopStatus`; unknown values are shown raw. */
export function currentStatusLabel(raw: string | null | undefined, t: Translate): string | null {
  if (!raw) return null
  // i18n-keys: vehicles.status.*
  return KNOWN_STATUSES.has(raw) ? t(`vehicles.status.${raw}`) : raw
}

const KNOWN_OCCUPANCY = new Set([
  'EMPTY',
  'MANY_SEATS_AVAILABLE',
  'FEW_SEATS_AVAILABLE',
  'STANDING_ROOM_ONLY',
  'CRUSHED_STANDING_ROOM_ONLY',
  'FULL',
  'NOT_ACCEPTING_PASSENGERS',
  'NO_DATA_AVAILABLE',
  'NOT_BOARDABLE',
])

/** Label for a GTFS-realtime occupancy status; unknown values are shown raw. */
export function occupancyLabel(raw: string, t: Translate): string {
  // i18n-keys: vehicles.occupancy.*
  return KNOWN_OCCUPANCY.has(raw) ? t(`vehicles.occupancy.${raw}`) : raw
}

const KNOWN_MATCH_STATUSES = new Set(['MATCHED', 'UNMATCHED', 'SKIPPED', 'PENDING'])

/** Label for an AVL report's match status; unknown values are shown raw. */
export function matchStatusLabel(raw: string, t: Translate): string {
  // i18n-keys: vehicles.matchStatus.*
  return KNOWN_MATCH_STATUSES.has(raw) ? t(`vehicles.matchStatus.${raw}`) : raw
}

/**
 * How long ago a report arrived, in whole units ("12s ago", "3m ago", "2h ago"). Shared by the
 * vehicle detail page and the telemetry panel.
 */
export function ageLabel(sec: number, t: Translate): string {
  if (sec < 45) return t('poll.secondsAgo', { count: Math.round(sec) })
  if (sec < 3600) return t('poll.minutesAgo', { count: Math.round(sec / 60) })
  return t('vehicles.ago.hours', { count: Math.round(sec / 3600) })
}

/** Same as [ageLabel] but reads "a few seconds ago" below 45 seconds (vehicle list and popup). */
export function coarseAgeLabel(sec: number, t: Translate): string {
  if (sec < 45) return t('vehicles.ago.fewSeconds')
  return ageLabel(sec, t)
}
