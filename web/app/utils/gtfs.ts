const ROUTE_TYPES: Record<number, string> = {
  0: 'Tram',
  1: 'Subway',
  2: 'Rail',
  3: 'Bus',
  4: 'Ferry',
  5: 'Cable tram',
  6: 'Aerial lift',
  7: 'Funicular',
  11: 'Trolleybus',
  12: 'Monorail',
}

export function routeTypeLabel(type: number | null | undefined): string {
  if (type == null) return '—'
  // extended route types collapse to their hundreds bucket
  return ROUTE_TYPES[type] ?? ROUTE_TYPES[Math.floor(type / 100)] ?? `Type ${type}`
}

export function hexColor(raw: string | null | undefined, fallback = '#64748b'): string {
  if (!raw) return fallback
  return raw.startsWith('#') ? raw : `#${raw}`
}

const DAYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'] as const

export interface DayFlags {
  monday: boolean | null
  tuesday: boolean | null
  wednesday: boolean | null
  thursday: boolean | null
  friday: boolean | null
  saturday: boolean | null
  sunday: boolean | null
}

export function activeDays(cal: DayFlags): boolean[] {
  return DAYS.map((d) => !!cal[d])
}

export function serviceDaysLabel(cal: DayFlags): string {
  const on = DAYS.filter((d) => cal[d])
  if (on.length === 7) return 'Every day'
  if (on.length === 5 && !cal.saturday && !cal.sunday) return 'Weekdays'
  if (on.length === 2 && cal.saturday && cal.sunday) return 'Weekends'
  if (on.length === 0) return 'None'
  return on.map((d) => d.slice(0, 3)).join(', ')
}

/** GTFS date "YYYYMMDD" or ISO → short display */
export function gtfsDate(raw: string | null | undefined): string {
  if (!raw) return '—'
  const s = raw.length === 8 ? `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}` : raw
  const d = new Date(s)
  return Number.isNaN(d.getTime()) ? s : d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

export type LngLat = [number, number]

interface StopPathLike {
  stopPathIndex: number
  pathGeometry?: unknown
  stop?: { stopName?: string | null, stopLat?: number | null, stopLon?: number | null } | null
  stopId: string
}

/** Concatenate a pattern's per-segment pathGeometry ([lon,lat] arrays) into one line. */
export function patternLine(stopPaths: StopPathLike[]): LngLat[] {
  const out: LngLat[] = []
  for (const p of [...stopPaths].sort((a, b) => a.stopPathIndex - b.stopPathIndex)) {
    const geom = p.pathGeometry
    if (!Array.isArray(geom)) continue
    for (const pt of geom) {
      if (Array.isArray(pt) && typeof pt[0] === 'number' && typeof pt[1] === 'number') {
        const last = out[out.length - 1]
        if (!last || last[0] !== pt[0] || last[1] !== pt[1]) out.push([pt[0], pt[1]])
      }
    }
  }
  return out
}

/** Pattern stops as a GeoJSON FeatureCollection (all marked "upcoming"). */
export function patternStopFeatures(stopPaths: StopPathLike[]) {
  return {
    type: 'FeatureCollection' as const,
    features: [...stopPaths]
      .sort((a, b) => a.stopPathIndex - b.stopPathIndex)
      .filter((p) => p.stop?.stopLat != null && p.stop?.stopLon != null)
      .map((p) => ({
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [p.stop!.stopLon as number, p.stop!.stopLat as number] as LngLat,
        },
        properties: { name: p.stop!.stopName ?? p.stopId, state: 'upcoming' },
      })),
  }
}
