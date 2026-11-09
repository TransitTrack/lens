import type { AvlReportMatch, MatchStatus, Route, Trip } from '~/api'
import { withRoute } from './routes'

/** A vehicle's AVL report with its matched trip and that trip's route, as the AVL log renders it. */
export type AvlLogRow = { ts: string, speedMps: number | null, bearing: number | null, matchStatus: MatchStatus, descTripId: string | null, position: { lat: number, lon: number }, match: { stopPathIndex: number, distanceAlongTripM: number, deviationM: number, scheduleAdherenceSec: number | null, score: number | null, trip: { tripId: string, tripHeadsign: string | null, route: { routeShortName: string | null, routeColor: string | null } | null } | null } | null }

const tripKey = (m: { revisionId: string, tripId: string }) => `${m.revisionId}|${m.tripId}`

/** The distinct (revision, trip) pairs the reports matched to. */
export function reportTripKeys(reports: Array<{ match?: AvlReportMatch | null }>): Array<{ revisionId: string, tripId: string }> {
  const keys = new Map<string, { revisionId: string, tripId: string }>()
  for (const r of reports) {
    if (r.match) keys.set(tripKey(r.match), { revisionId: r.match.revisionId, tripId: r.match.tripId })
  }
  return [...keys.values()]
}

/**
 * Joins each report's matched trip (and, when `routes` is given, the trip's route) onto it. `trips` is
 * keyed `${revisionId}|${tripId}`.
 */
export function reportsWithTrips<T extends { match?: AvlReportMatch | null }>(
  reports: T[],
  trips: Map<string, Trip | null>,
  routes?: Map<string, Route>,
) {
  return reports.map((r) => {
    if (!r.match) return { ...r, match: null }
    const trip = trips.get(tripKey(r.match)) ?? null
    const joined = trip && routes ? withRoute(trip, routes) : trip
    return { ...r, match: { ...r.match, trip: joined } }
  })
}

/** Each revision's route ids, from trips keyed `${revisionId}|${tripId}` (unknown trips skipped). */
export function reportRouteIdsByRevision(trips: Map<string, Trip | null>): Record<string, string[]> {
  const byRevision: Record<string, Set<string>> = {}
  for (const [key, trip] of trips) {
    if (!trip) continue
    const revisionId = key.slice(0, key.indexOf('|'))
    ;(byRevision[revisionId] ??= new Set()).add(trip.routeId)
  }
  return Object.fromEntries(Object.entries(byRevision).map(([revisionId, ids]) => [revisionId, [...ids]]))
}

/**
 * [reportsWithTrips] with each report's route taken from its own revision's routes (`routes` keyed by
 * revision id), so routes of different revisions are never mixed. Reports keep their order.
 */
export function reportsWithTripsByRevision<T extends { match?: AvlReportMatch | null }>(
  reports: T[],
  trips: Map<string, Trip | null>,
  routes: Map<string, Map<string, Route>>,
) {
  return reports.map((r) => reportsWithTrips([r], trips, (r.match && routes.get(r.match.revisionId)) || new Map())[0]!)
}
