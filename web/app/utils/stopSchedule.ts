import type {Pattern} from '~/composables/useExplorePatterns'
import type {Bar} from './chart'

export interface ServingPattern {
  pattern: Pattern
  /** this stop's index within the pattern */
  stopPathIndex: number
  /** typical seconds from the pattern's first stop to this one */
  offsetSec: number
}

/** Patterns that call at `stopId`, with the cumulative travel time to it. */
export function servingPatterns(patterns: Pattern[], stopId: string): ServingPattern[] {
  const out: ServingPattern[] = []
  for (const pattern of patterns) {
    const sp = pattern.stopPaths.find((s) => s.stopId === stopId)
    if (!sp) continue
    let offsetSec = 0
    for (const s of pattern.stopPaths) {
      if (s.stopPathIndex > sp.stopPathIndex) break
      if (s.stopPathIndex === 0) continue
      offsetSec += (s.typicalTravelTimeSec ?? 0) + (s.typicalDwellTimeSec ?? 0)
    }
    out.push({pattern, stopPathIndex: sp.stopPathIndex, offsetSec})
  }
  return out
}

export type ServiceKind = 'weekday' | 'saturday' | 'sunday'

interface CalendarLike {
  serviceId: string
  monday: boolean | null
  tuesday: boolean | null
  wednesday: boolean | null
  thursday: boolean | null
  friday: boolean | null
  saturday: boolean | null
  sunday: boolean | null
}

/** Map each serviceId to the kinds of day it runs on. */
export function serviceKinds(calendars: CalendarLike[]): Map<string, Set<ServiceKind>> {
  const map = new Map<string, Set<ServiceKind>>()
  for (const c of calendars) {
    const kinds = new Set<ServiceKind>()
    if (c.monday || c.tuesday || c.wednesday || c.thursday || c.friday) kinds.add('weekday')
    if (c.saturday) kinds.add('saturday')
    if (c.sunday) kinds.add('sunday')
    map.set(c.serviceId, kinds)
  }
  return map
}

export interface DepartureInput {
  patternKey: string
  trips: { startTimeSec: number | null, serviceId: string }[]
}

export interface ScheduledDepartureInput {
  patternKey: string
  trips: {
    startTimeSec: number | null
    serviceId: string
    scheduleTimes: { stopPathIndex: number, departureSec: number | null }[]
  }[]
}

export interface ScheduledDeparture {
  routeLabel: string
  routeColor: string | null
  routeTextColor: string | null
  headsign: string | null
  departureSec: number
}

export interface ScheduledDepartureGroup {
  hour: number
  departures: ScheduledDeparture[]
}

/** Exact scheduled departures at a stop, grouped by service-hour for a timetable. */
export function scheduledDepartureGroups(
  serving: ServingPattern[],
  tripsByPattern: ScheduledDepartureInput[],
  kinds: Map<string, Set<ServiceKind>>,
  kind: ServiceKind,
): ScheduledDepartureGroup[] {
  const tripsByPatternKey = new Map(tripsByPattern.map((item) => [item.patternKey, item.trips]))
  const departures: ScheduledDeparture[] = []

  for (const served of serving) {
    const trips = tripsByPatternKey.get(served.pattern.patternKey) ?? []
    for (const trip of trips) {
      if (!kinds.get(trip.serviceId)?.has(kind)) continue
      const departureSec = trip.scheduleTimes.find(
        (time) => time.stopPathIndex === served.stopPathIndex,
      )?.departureSec
      if (departureSec == null) continue
      departures.push({
        routeLabel: served.pattern.route?.routeShortName ?? served.pattern.routeId,
        routeColor: served.pattern.route?.routeColor ?? null,
        routeTextColor: served.pattern.route?.routeTextColor ?? null,
        headsign: served.pattern.headsign,
        departureSec,
      })
    }
  }

  departures.sort((a, b) => a.departureSec - b.departureSec)
  const groups = new Map<number, ScheduledDeparture[]>()
  for (const departure of departures) {
    const hour = Math.floor(departure.departureSec / 3600)
    const group = groups.get(hour) ?? []
    group.push(departure)
    groups.set(hour, group)
  }
  return [...groups].map(([hour, departures]) => ({hour, departures}))
}

/** 24-bar histogram of scheduled departures at the stop for one service kind. */
export function departureHistogram(
  serving: ServingPattern[],
  tripsByPattern: DepartureInput[],
  kinds: Map<string, Set<ServiceKind>>,
  kind: ServiceKind,
): { bars: Bar[], total: number, firstSec: number | null, lastSec: number | null } {
  const byPatternKey = new Map(tripsByPattern.map((t) => [t.patternKey, t.trips]))
  const hours = new Array(24).fill(0)
  let total = 0
  let firstSec: number | null = null
  let lastSec: number | null = null

  for (const s of serving) {
    const trips = byPatternKey.get(s.pattern.patternKey) ?? []
    for (const t of trips) {
      if (t.startTimeSec == null) continue
      if (!kinds.get(t.serviceId)?.has(kind)) continue
      const dep = t.startTimeSec + s.offsetSec
      hours[Math.floor(dep / 3600) % 24]++
      total++
      if (firstSec == null || dep < firstSec) firstSec = dep
      if (lastSec == null || dep > lastSec) lastSec = dep
    }
  }

  const bars: Bar[] = hours.map((count, h) => ({
    label: h % 3 === 0 ? String(h) : '',
    value: count,
    color: '#3b82f6',
    hint: `${h}:00–${h + 1}:00 · ${count} departures`,
  }))
  return {bars, total, firstSec, lastSec}
}
