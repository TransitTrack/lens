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

export function secToHm(sec: number | null): string {
  if (sec == null) return '—'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return `${String(h % 24).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

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
  return Number.isNaN(d.getTime()) ? s : d.toLocaleDateString(undefined, {dateStyle: 'medium'})
}

export type LngLat = [number, number]

interface StopPathLike {
  stopPathIndex: number
  pathGeometry?: unknown
  stop?: { stopName?: string | null, stopLat?: number | null, stopLon?: number | null } | null
  stopId?: string
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

function perpendicularDistanceSq(p: LngLat, a: LngLat, b: LngLat): number {
  const [x, y] = p
  const [x1, y1] = a
  const [x2, y2] = b
  const dx = x2 - x1
  const dy = y2 - y1
  const lenSq = dx * dx + dy * dy
  if (lenSq === 0) {
    const ex = x - x1
    const ey = y - y1
    return ex * ex + ey * ey
  }
  const t = ((x - x1) * dx + (y - y1) * dy) / lenSq
  const cx = x1 + t * dx
  const cy = y1 + t * dy
  const ex = x - cx
  const ey = y - cy
  return ex * ex + ey * ey
}

/**
 * Ramer–Douglas–Peucker line simplification. Route shapes come back from GTFS
 * shape points at raw GPS resolution — mostly near-collinear points along
 * straight road segments — so a small tolerance (in degrees; ~0.00003 is
 * roughly 3m at mid latitudes) drops the majority of points with no visible
 * difference at the zoom levels these lines are actually viewed at, while
 * cutting the JS building + GeoJSON-to-worker transfer cost proportionally.
 */
export function simplifyLine(points: LngLat[], toleranceDeg: number): LngLat[] {
  if (points.length <= 2) return points
  const toleranceSq = toleranceDeg * toleranceDeg
  const keep = new Uint8Array(points.length)
  keep[0] = 1
  keep[points.length - 1] = 1

  const stack: [number, number][] = [[0, points.length - 1]]
  while (stack.length) {
    const [start, end] = stack.pop()!
    let maxDistSq = 0
    let maxIndex = -1
    for (let i = start + 1; i < end; i++) {
      const distSq = perpendicularDistanceSq(points[i]!, points[start]!, points[end]!)
      if (distSq > maxDistSq) {
        maxDistSq = distSq
        maxIndex = i
      }
    }
    if (maxDistSq > toleranceSq && maxIndex !== -1) {
      keep[maxIndex] = 1
      stack.push([start, maxIndex], [maxIndex, end])
    }
  }

  return points.filter((_, i) => keep[i])
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
        properties: {name: p.stop!.stopName ?? p.stopId, state: 'upcoming'},
      })),
  }
}
