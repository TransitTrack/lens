import type {Bar} from './chart'
import type {Translate} from './gtfs'
import type {StopSchedule, StopScheduleCall, StopServiceDays} from '~/api'

export type ServiceKind = 'weekday' | 'saturday' | 'sunday'

/** Map each serviceId to the kinds of day it runs on (services the backend didn't list run on none). */
export function serviceKinds(services: StopServiceDays[]): Map<string, Set<ServiceKind>> {
  const map = new Map<string, Set<ServiceKind>>()
  for (const service of services) {
    const kinds = new Set<ServiceKind>()
    if (service.weekday) kinds.add('weekday')
    if (service.saturday) kinds.add('saturday')
    if (service.sunday) kinds.add('sunday')
    map.set(service.serviceId, kinds)
  }
  return map
}

export interface StopRoute {
  id: string
  name: string
  color: string | null
}

/** The distinct routes among the calls, in first-seen order. */
export function routesOfCalls(calls: StopScheduleCall[]): StopRoute[] {
  const routes = new Map<string, StopRoute>()
  for (const call of calls) {
    if (routes.has(call.routeId)) continue
    routes.set(call.routeId, {id: call.routeId, name: call.routeShortName ?? call.routeId, color: call.routeColor ?? null})
  }
  return [...routes.values()]
}

/** Trips across the patterns calling at the stop, and how many run in each direction. */
export function tripTotalsOf(calls: StopScheduleCall[]): {total: number, dir0: number, dir1: number} {
  let total = 0
  let dir0 = 0
  let dir1 = 0
  for (const call of calls) {
    total += call.tripCount
    if (call.directionId === 0) dir0 += call.tripCount
    else if (call.directionId === 1) dir1 += call.tripCount
  }
  return {total, dir0, dir1}
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

/** The departures at the stop that run on `kind` of day, earliest first. */
function departuresOfKind(
  schedule: StopSchedule,
  kinds: Map<string, Set<ServiceKind>>,
  kind: ServiceKind,
): ScheduledDeparture[] {
  const out: ScheduledDeparture[] = []
  for (const call of schedule.calls) {
    for (const departure of call.departures) {
      if (!kinds.get(departure.serviceId)?.has(kind)) continue
      out.push({
        routeLabel: call.routeShortName ?? call.routeId,
        routeColor: call.routeColor ?? null,
        routeTextColor: call.routeTextColor ?? null,
        headsign: call.headsign ?? null,
        departureSec: departure.departureSec,
      })
    }
  }
  return out.sort((a, b) => a.departureSec - b.departureSec)
}

/** Exact scheduled departures at a stop, grouped by service-hour for a timetable. */
export function scheduledDepartureGroups(
  schedule: StopSchedule,
  kinds: Map<string, Set<ServiceKind>>,
  kind: ServiceKind,
): ScheduledDepartureGroup[] {
  const groups = new Map<number, ScheduledDeparture[]>()
  for (const departure of departuresOfKind(schedule, kinds, kind)) {
    const hour = Math.floor(departure.departureSec / 3600)
    const group = groups.get(hour) ?? []
    group.push(departure)
    groups.set(hour, group)
  }
  return [...groups].map(([hour, departures]) => ({hour, departures}))
}

/** 24-bar histogram of scheduled departures at the stop for one service kind. */
export function departureHistogram(
  schedule: StopSchedule,
  kinds: Map<string, Set<ServiceKind>>,
  kind: ServiceKind,
  t: Translate,
): { bars: Bar[], total: number, firstSec: number | null, lastSec: number | null } {
  const hours = new Array(24).fill(0)
  const departures = departuresOfKind(schedule, kinds, kind)
  for (const departure of departures) hours[Math.floor(departure.departureSec / 3600) % 24]++

  const bars: Bar[] = hours.map((count, h) => ({
    label: h % 3 === 0 ? String(h) : '',
    value: count,
    color: '#3b82f6',
    hint: t('explore.stop.hourHint', { from: h, to: h + 1, count }, count),
  }))
  return {
    bars,
    total: departures.length,
    firstSec: departures[0]?.departureSec ?? null,
    lastSec: departures.at(-1)?.departureSec ?? null,
  }
}
