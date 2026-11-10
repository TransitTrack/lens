import { displayLocale } from './intlLocale'
import { decodeGeoJson } from './geojson'

const KNOWN_ROUTE_TYPES = new Set([0, 1, 2, 3, 4, 5, 6, 7, 11, 12])

export type Translate = (key: string, params?: Record<string, unknown>, plural?: number) => string

export function routeTypeLabel(type: number | null | undefined, t: Translate): string {
  if (type == null) return '—'
  // extended route types collapse to their hundreds bucket
  const known = KNOWN_ROUTE_TYPES.has(type)
    ? type
    : KNOWN_ROUTE_TYPES.has(Math.floor(type / 100))
      ? Math.floor(type / 100)
      : null
  // i18n-keys: gtfs.routeType.*
  return known != null ? t(`gtfs.routeType.${known}`) : t('gtfs.routeTypeOther', { type })
}

export function hexColor(raw: string | null | undefined, fallback = '#64748b'): string {
  const value = raw?.replace('#', '').trim()
  if (!value || !/^(?:[\da-f]{3}|[\da-f]{6})$/i.test(value)) return fallback
  const expanded = value.length === 3 ? value.split('').map((c) => c + c).join('') : value
  return `#${expanded}`
}

export function routeTextColor(routeColor: string, declared: string | null | undefined): string {
  const candidate = declared ? hexColor(declared) : null
  if (candidate && contrastRatio(routeColor, candidate) >= 4.5) return candidate
  return relativeLuminance(routeColor) > 0.35 ? '#18181b' : '#fafafa'
}

function relativeLuminance(hex: string): number {
  const channels = hex.slice(1).match(/.{2}/g)!.map((part) => Number.parseInt(part, 16) / 255)
  const linear = channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
  return 0.2126 * linear[0]! + 0.7152 * linear[1]! + 0.0722 * linear[2]!
}

function contrastRatio(a: string, b: string): number {
  const [lighter, darker] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return (lighter! + 0.05) / (darker! + 0.05)
}


const DAYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'] as const

export function secToHm(sec: number | null): string {
  if (sec == null) return '—'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return `${String(h % 24).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export interface DayFlags {
  monday?: boolean | null
  tuesday?: boolean | null
  wednesday?: boolean | null
  thursday?: boolean | null
  friday?: boolean | null
  saturday?: boolean | null
  sunday?: boolean | null
}

export function activeDays(cal: DayFlags): boolean[] {
  return DAYS.map((d) => !!cal[d])
}

export function serviceDaysLabel(cal: DayFlags, t: Translate): string {
  const on = DAYS.filter((d) => cal[d])
  if (on.length === 7) return t('explore.serviceDays.everyDay')
  if (on.length === 5 && !cal.saturday && !cal.sunday) return t('explore.serviceDays.weekdays')
  if (on.length === 2 && cal.saturday && cal.sunday) return t('explore.serviceDays.weekends')
  if (on.length === 0) return t('explore.serviceDays.none')
  // i18n-keys: explore.dayShort.*
  return on.map((d) => t(`explore.dayShort.${d}`)).join(', ')
}

/** GTFS date "YYYYMMDD" or ISO → short display */
export function gtfsDate(raw: string | null | undefined, appLocale?: string): string {
  if (!raw) return '—'
  const s = raw.length === 8 ? `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}` : raw
  const d = new Date(s)
  return Number.isNaN(d.getTime()) ? s : d.toLocaleDateString(displayLocale(appLocale), {dateStyle: 'medium'})
}

export type LngLat = [number, number]

interface StopPathLike {
  stopPathIndex: number
  stop?: { stopName?: string | null, stopLat?: number | null, stopLon?: number | null } | null
  stopId?: string
}

/**
 * A pattern's `geometry` (GeoJSON LineString, coordinates [lon,lat], gzip + base64 encoded by the
 * backend) as a plain line; [] if absent or undecodable.
 */
export function geometryLine(encoded: string | null | undefined): LngLat[] {
  if (!encoded) return []
  let coords: unknown
  try {
    coords = decodeGeoJson<{ coordinates?: unknown }>(encoded).coordinates
  } catch {
    return []
  }
  if (!Array.isArray(coords)) return []
  const out: LngLat[] = []
  for (const pt of coords) {
    if (Array.isArray(pt) && typeof pt[0] === 'number' && typeof pt[1] === 'number') {
      const last = out[out.length - 1]
      if (!last || last[0] !== pt[0] || last[1] !== pt[1]) out.push([pt[0], pt[1]])
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
        properties: {name: p.stop!.stopName ?? p.stopId, stopId: p.stopId, state: 'upcoming'},
      })),
  }
}
