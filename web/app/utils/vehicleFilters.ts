import type { VehicleRow } from '../composables/useVehiclePolling'

/**
 * What a vehicle is doing right now. Every vehicle is in exactly one state, so the per-state
 * counts add up to the vehicle total; [vehicleState] checks them in this order.
 */
export type VehicleState = 'unmatched' | 'stale' | 'terminus' | 'late' | 'early' | 'ontime'

export const VEHICLE_STATES: { value: VehicleState; label: string; dotClass: string }[] = [
  { value: 'ontime', label: 'On time', dotClass: 'bg-success' },
  { value: 'late', label: 'Late', dotClass: 'bg-error' },
  { value: 'early', label: 'Early', dotClass: 'bg-warning' },
  { value: 'terminus', label: 'At terminus', dotClass: 'bg-info' },
  { value: 'stale', label: 'Lost signal', dotClass: 'bg-neutral-500' },
  { value: 'unmatched', label: 'Not matched', dotClass: 'bg-neutral-300 dark:bg-neutral-600' },
]

/** Same ±1 minute band as the adherence colours (see `adherenceBadge`). */
const ON_TIME_WINDOW_SEC = 60
/** At or below this a vehicle counts as standing. */
const STANDING_MPS = 0.5
/** How close to either end of its trip a standing vehicle must be to count as at a terminus. */
const TERMINUS_RADIUS_M = 50

function atTerminus(v: VehicleRow): boolean {
  if ((v.speedMps ?? 0) > STANDING_MPS) return false
  if (v.stopPathIndex === 0) return true
  const along = v.distanceAlongTripM
  if (along == null) return false
  const lengthM = v.pattern?.lengthM
  return along <= TERMINUS_RADIUS_M || (lengthM != null && along >= lengthM - TERMINUS_RADIUS_M)
}

export function vehicleState(v: VehicleRow): VehicleState {
  if (!v.matched) return 'unmatched'
  if (v.stale) return 'stale'
  // before late/early: a bus waiting out a layover isn't "29 minutes late"
  if (atTerminus(v)) return 'terminus'
  const sec = v.scheduleAdherenceSec
  if (sec != null && sec > ON_TIME_WINDOW_SEC) return 'late'
  if (sec != null && sec < -ON_TIME_WINDOW_SEC) return 'early'
  return 'ontime'
}

export interface VehicleFilterState {
  query: string
  /** one state at a time; `null` = all */
  state: VehicleState | null
  /** any of these routes; empty = all */
  routeIds: string[]
}

export function emptyFilters(): VehicleFilterState {
  return { query: '', state: null, routeIds: [] }
}

/**
 * The trip a vehicle is on, if it's matched. An unmatched vehicle can still carry the trip it last
 * had, which no longer says anything about it.
 */
function currentTrip(v: VehicleRow) {
  return v.matched ? v.trip : null
}

function matchesQuery(v: VehicleRow, query: string): boolean {
  if (!query) return true
  const trip = currentTrip(v)
  const searchable = [
    v.vehicleId,
    v.label,
    trip?.routeId,
    trip?.route?.routeShortName,
    trip?.tripHeadsign,
    trip?.tripId,
    v.matched ? v.block?.blockId : null,
  ]
  return searchable.some((value) => value?.toLocaleLowerCase().includes(query))
}

function matchesRoutes(v: VehicleRow, routeIds: string[]): boolean {
  if (!routeIds.length) return true
  const routeId = currentTrip(v)?.routeId
  return routeId != null && routeIds.includes(routeId)
}

function normalisedQuery(filters: VehicleFilterState): string {
  return filters.query.trim().toLocaleLowerCase()
}

export function filterVehicles(vehicles: VehicleRow[], filters: VehicleFilterState): VehicleRow[] {
  const query = normalisedQuery(filters)
  return vehicles.filter(
    (v) =>
      matchesQuery(v, query) &&
      matchesRoutes(v, filters.routeIds) &&
      (filters.state == null || vehicleState(v) === filters.state),
  )
}

export type StateCounts = Record<VehicleState | 'all', number>

/** Vehicles per state under the current search and routes — what each state would show if picked. */
export function stateCounts(vehicles: VehicleRow[], filters: VehicleFilterState): StateCounts {
  const counts: StateCounts = {
    all: 0,
    ontime: 0,
    late: 0,
    early: 0,
    terminus: 0,
    stale: 0,
    unmatched: 0,
  }
  const query = normalisedQuery(filters)
  for (const v of vehicles) {
    if (!matchesQuery(v, query) || !matchesRoutes(v, filters.routeIds)) continue
    counts.all++
    counts[vehicleState(v)]++
  }
  return counts
}

export interface RouteFacet {
  routeId: string
  label: string
  color: string | null
  textColor: string | null
  /** vehicles on this route in the chosen state */
  count: number
}

/**
 * Routes that currently have a matched vehicle (under the search), each counted for the chosen
 * state, busiest first. Selected routes stay listed even with no vehicles left, so they can be
 * unticked.
 */
export function routeFacets(vehicles: VehicleRow[], filters: VehicleFilterState): RouteFacet[] {
  const query = normalisedQuery(filters)
  const byRoute = new Map<string, RouteFacet>()
  for (const v of vehicles) {
    const trip = currentTrip(v)
    if (!trip || !matchesQuery(v, query)) continue
    const facet = byRoute.get(trip.routeId) ?? {
      routeId: trip.routeId,
      label: trip.route?.routeShortName ?? trip.routeId,
      color: trip.route?.routeColor ?? null,
      textColor: trip.route?.routeTextColor ?? null,
      count: 0,
    }
    if (filters.state == null || vehicleState(v) === filters.state) facet.count++
    byRoute.set(trip.routeId, facet)
  }
  for (const routeId of filters.routeIds) {
    if (!byRoute.has(routeId)) {
      byRoute.set(routeId, { routeId, label: routeId, color: null, textColor: null, count: 0 })
    }
  }
  return [...byRoute.values()].sort(
    (a, b) => b.count - a.count || a.label.localeCompare(b.label, undefined, { numeric: true }),
  )
}

/**
 * Vehicle list order: vehicles matched to a trip first, then the rest; within each group by label
 * (falling back to the id) in natural order, so `5002` sorts before `5010`. [descending] reverses
 * that label order but keeps matched vehicles on top.
 */
export function sortVehicles(vehicles: VehicleRow[], descending = false): VehicleRow[] {
  return [...vehicles].sort((a, b) => {
    if (a.matched !== b.matched) return a.matched ? -1 : 1
    const order = (a.label ?? a.vehicleId).localeCompare(b.label ?? b.vehicleId, undefined, {
      numeric: true,
    })
    return descending ? -order : order
  })
}
