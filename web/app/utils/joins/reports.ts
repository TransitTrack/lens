import type { AvlReportMatch, Route, Trip } from '~/api'
import { withRoute } from './routes'

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
