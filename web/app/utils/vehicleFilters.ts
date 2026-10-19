import type { VehicleRow } from '../composables/useVehiclePolling'

export type VehicleStatus =
  | 'matched' | 'assigned' | 'layover' | 'stale' | 'unmatched' | 'attention' | 'late' | 'early' | 'ontime'

export const STATUS_OPTIONS: { value: VehicleStatus; label: string }[] = [
  { value: 'matched', label: 'Matched' },
  { value: 'assigned', label: 'Assigned' },
  { value: 'layover', label: 'Layover' },
  { value: 'unmatched', label: 'Unmatched' },
  { value: 'stale', label: 'Stale' },
  { value: 'attention', label: 'Needs attention' },
  { value: 'late', label: 'Late' },
  { value: 'early', label: 'Early' },
  { value: 'ontime', label: 'On time' },
]

export interface VehicleFilterState {
  query: string
  routeId: string | null
  statuses: VehicleStatus[]
}

export function emptyFilters(): VehicleFilterState {
  return { query: '', routeId: null, statuses: [] }
}

export function matchesVehicleStatus(v: VehicleRow, status: VehicleStatus): boolean {
  const sec = v.scheduleAdherenceSec
  switch (status) {
    case 'matched':
      return v.matched
    case 'assigned':
      return v.matched && !v.stale
    case 'layover':
      return v.matched && !v.stale && v.speedMps != null && v.speedMps <= 0.5
    case 'unmatched':
      return !v.matched
    case 'stale':
      return v.stale
    case 'attention':
      return v.stale || !v.matched
    case 'late':
      return v.matched && !v.stale && sec != null && sec >= 60
    case 'early':
      return v.matched && !v.stale && sec != null && sec <= -60
    case 'ontime':
      return v.matched && !v.stale && sec != null && sec > -60 && sec < 60
  }
}

export function filterVehicles(vehicles: VehicleRow[], filters: VehicleFilterState): VehicleRow[] {
  const query = filters.query.trim().toLocaleLowerCase()

  return vehicles.filter((v) => {
    if (query) {
      const searchable = [
        v.vehicleId,
        v.label,
        v.trip?.routeId,
        v.trip?.route?.routeShortName,
        v.trip?.route?.routeLongName,
        v.trip?.tripHeadsign,
      ]
      if (!searchable.some((value) => value?.toLocaleLowerCase().includes(query))) return false
    }
    if (filters.routeId && v.trip?.routeId !== filters.routeId) return false
    if (filters.statuses.length && !filters.statuses.every((s) => matchesVehicleStatus(v, s))) {
      return false
    }
    return true
  })
}
