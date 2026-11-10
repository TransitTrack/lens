import { defineQueryOptions } from '@pinia/colada'
import { type Agency, type Calendar, type CalendarDate, type EntityFindingCount, type Feed, type FeedInfo, Gtfs, optional, type Revision, type RevisionStatus, type Route, type Shape, type Stop, type StopTime, type Trip, type ValidationFinding } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'
import { feedKeys, type Rev } from '../keys/feeds'
import { cachePolicy, revisionParam } from './cachePolicy'

/** GET /feeds/{feedCode}/agencies — every Agency row (all pages). */
export const agenciesQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'agencies'),
  query: ({ signal }): Promise<Agency[]> =>
    fetchAllPages((offset, limit) => Gtfs.agencies({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/calendar-dates — every CalendarDate row (all pages). */
export const calendarDatesQuery = defineQueryOptions((p: Rev & { serviceId?: string }) => ({
  key: feedKeys.resource(p, 'calendar-dates', { serviceId: p.serviceId }),
  query: ({ signal }): Promise<CalendarDate[]> =>
    fetchAllPages((offset, limit) => Gtfs.calendarDates({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), serviceId: p.serviceId, offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/calendars — every Calendar row (all pages). */
export const calendarsQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'calendars'),
  query: ({ signal }): Promise<Calendar[]> =>
    fetchAllPages((offset, limit) => Gtfs.calendars({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/feed-info — one FeedInfo, or null when unknown. */
export const feedInfoQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'feed-info'),
  query: ({ signal }): Promise<FeedInfo | null> =>
    optional(Gtfs.feedInfo({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{code} — one feed, or null when unknown. */
export const feedQuery = defineQueryOptions((p: { code: string }) => ({
  key: feedKeys.feed(p.code),
  query: ({ signal }): Promise<Feed | null> =>
    optional(Gtfs.feed({ path: { code: p.code }, signal })),
}))

/** GET /feeds — every configured GTFS feed (all pages). */
export const feedsQuery = defineQueryOptions({
  key: feedKeys.list(),
  query: ({ signal }): Promise<Feed[]> =>
    fetchAllPages((offset, limit) => Gtfs.feeds({ query: { offset, limit }, signal })),
})

/** GET /feeds/{feedCode}/revisions — every revision of the feed, optionally filtered by status (all pages). */
export const revisionsQuery = defineQueryOptions((p: { feedCode: string, status?: RevisionStatus }) => ({
  key: feedKeys.revisions(p.feedCode, { status: p.status }),
  query: ({ signal }): Promise<Revision[]> =>
    fetchAllPages((offset, limit) => Gtfs.revisions({ path: { feedCode: p.feedCode }, query: { status: p.status, offset, limit }, signal })),
}))

/** GET /feeds/{feedCode}/routes/{routeId} — one Route, or null when unknown. */
export const routeQuery = defineQueryOptions((p: Rev & { routeId: string }) => ({
  key: feedKeys.resource(p, 'routes', { routeId: p.routeId }),
  query: ({ signal }): Promise<Route | null> =>
    optional(Gtfs.route({ path: { feedCode: p.feedCode, routeId: p.routeId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/routes/{routeId}/stops — every Stop row (all pages). Fresh for 60 s on the active revision. */
export const routeStopsQuery = defineQueryOptions((p: Rev & { routeId: string }) => ({
  key: feedKeys.resource(p, 'route-stops', { routeId: p.routeId }),
  query: ({ signal }): Promise<Stop[]> =>
    fetchAllPages((offset, limit) => Gtfs.routeStops({ path: { feedCode: p.feedCode, routeId: p.routeId }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))

/** Ids per request, keeping the repeated `?routeId=` query well under URL length limits. */
const IDS_PER_REQUEST = 100

/**
 * GET /feeds/{feedCode}/routes?routeId=… — only the given routes (ids de-duplicated and sorted, 100 per request, all pages
 * of each). An empty id list resolves to `[]` without a request. Fresh for 60 s on the active revision.
 */
export const routesByIdsQuery = defineQueryOptions((p: Rev & { routeIds: string[] }) => {
  const routeIds = [...new Set(p.routeIds.filter(Boolean))].sort()
  return {
    key: feedKeys.resource(p, 'routes', { routeIds }),
    query: async ({ signal }): Promise<Route[]> => {
      const chunks: string[][] = []
      for (let i = 0; i < routeIds.length; i += IDS_PER_REQUEST) chunks.push(routeIds.slice(i, i + IDS_PER_REQUEST))
      const pages = await Promise.all(chunks.map((routeId) =>
        fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), routeId, offset, limit }, signal }))))
      return pages.flat()
    },
    ...cachePolicy(p, 'activeList'),
  }
})

/** Ids per request, keeping the repeated `?routeId=` query well under URL length limits. */

/**
 * GET /feeds/{feedCode}/routes?revisionId=…&routeId=… — the given routes of each revision, read pinned to that
 * revision (ids de-duplicated and sorted, 100 per request, all pages of each). Returns `Map<revisionId, Map<routeId, Route>>`
 * so routes of different revisions are never merged. Revisions without ids are skipped; none at all resolves to an
 * empty map without a request. Revision-pinned, so it never goes stale.
 */
export const routesByRevisionQuery = defineQueryOptions((p: { feedCode: string, routeIdsByRevision: Record<string, string[]> }) => {
  const revisions = Object.entries(p.routeIdsByRevision)
    .map(([revisionId, ids]) => [revisionId, [...new Set(ids.filter(Boolean))].sort()] as [string, string[]])
    .filter(([, ids]) => ids.length > 0)
    .sort(([a], [b]) => a.localeCompare(b))
  return {
    // Under the feed, not its active-revision scope: every read here is pinned to an explicit revision.
    key: [...feedKeys.feed(p.feedCode), 'routes-by-revision', { revisions }] as const,
    query: async ({ signal }): Promise<Map<string, Map<string, Route>>> => {
      const entries = await Promise.all(revisions.map(async ([revisionId, routeIds]) => {
        const chunks: string[][] = []
        for (let i = 0; i < routeIds.length; i += IDS_PER_REQUEST) chunks.push(routeIds.slice(i, i + IDS_PER_REQUEST))
        const pages = await Promise.all(chunks.map((routeId) =>
          fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId, routeId, offset, limit }, signal }))))
        return [revisionId, new Map(pages.flat().map((route) => [route.routeId, route]))] as const
      }))
      return new Map(entries)
    },
    staleTime: Infinity,
  }
})

/** GET /feeds/{feedCode}/routes — every Route row (all pages). Fresh for 60 s on the active revision. */
export const routesQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'routes'),
  query: ({ signal }): Promise<Route[]> =>
    fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))

/** GET /feeds/{feedCode}/shapes/{shapeId} — one Shape, or null when unknown. */
export const shapeQuery = defineQueryOptions((p: Rev & { shapeId: string }) => ({
  key: feedKeys.resource(p, 'shapes', { shapeId: p.shapeId }),
  query: ({ signal }): Promise<Shape | null> =>
    optional(Gtfs.shape({ path: { feedCode: p.feedCode, shapeId: p.shapeId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/stops/{stopId} — one Stop, or null when unknown. */
export const stopQuery = defineQueryOptions((p: Rev & { stopId: string }) => ({
  key: feedKeys.resource(p, 'stops', { stopId: p.stopId }),
  query: ({ signal }): Promise<Stop | null> =>
    optional(Gtfs.stop({ path: { feedCode: p.feedCode, stopId: p.stopId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/trips/{tripId}/stop-times — every StopTime row (all pages). */
export const stopTimesQuery = defineQueryOptions((p: Rev & { tripId: string }) => ({
  key: feedKeys.resource(p, 'stop-times', { tripId: p.tripId }),
  query: ({ signal }): Promise<StopTime[]> =>
    fetchAllPages((offset, limit) => Gtfs.stopTimes({ path: { feedCode: p.feedCode, tripId: p.tripId }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** Ids per request, keeping the repeated `?stopId=` query well under URL length limits. */

/**
 * GET /feeds/{feedCode}/stops?stopId=… — only the given stops (ids de-duplicated and sorted, 100 per request, all pages
 * of each). An empty id list resolves to `[]` without a request. Fresh for 60 s on the active revision.
 */
export const stopsByIdsQuery = defineQueryOptions((p: Rev & { stopIds: string[] }) => {
  const stopIds = [...new Set(p.stopIds.filter(Boolean))].sort()
  return {
    key: feedKeys.resource(p, 'stops', { stopIds }),
    query: async ({ signal }): Promise<Stop[]> => {
      const chunks: string[][] = []
      for (let i = 0; i < stopIds.length; i += IDS_PER_REQUEST) chunks.push(stopIds.slice(i, i + IDS_PER_REQUEST))
      const pages = await Promise.all(chunks.map((stopId) =>
        fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), stopId, offset, limit }, signal }))))
      return pages.flat()
    },
    ...cachePolicy(p, 'activeList'),
  }
})

/** GET /feeds/{feedCode}/stops — every Stop row (all pages). Fresh for 60 s on the active revision. */
export const stopsQuery = defineQueryOptions((p: Rev) => ({
  key: feedKeys.resource(p, 'stops'),
  query: ({ signal }): Promise<Stop[]> =>
    fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), offset, limit }, signal })),
  ...cachePolicy(p, 'activeList'),
}))

/** GET /feeds/{feedCode}/trips/{tripId} — one Trip, or null when unknown. */
export const tripQuery = defineQueryOptions((p: Rev & { tripId: string }) => ({
  key: feedKeys.resource(p, 'trips', { tripId: p.tripId }),
  query: ({ signal }): Promise<Trip | null> =>
    optional(Gtfs.trip({ path: { feedCode: p.feedCode, tripId: p.tripId }, query: { revisionId: revisionParam(p) }, signal })),
  ...cachePolicy(p),
}))

/**
 * GET /feeds/{feedCode}/trips/{tripId}?revisionId=… — one request per distinct (revision, trip) key (de-duplicated and
 * sorted). The result is keyed `${revisionId}|${tripId}`, the format `reportsWithTrips` expects. A trip that no longer
 * exists (404) or whose read fails resolves to `null`, so one bad trip never drops the others. An empty key list
 * resolves to an empty map without a request. Revision-pinned, so it never goes stale.
 */
export const tripsByKeysQuery = defineQueryOptions((p: { feedCode: string, keys: Array<{ revisionId: string, tripId: string }> }) => {
  const keys = [...new Set(p.keys.map((k) => `${k.revisionId}|${k.tripId}`))].sort()
  return {
    // Under the feed, not its active-revision scope: every read here is pinned to an explicit revision.
    key: [...feedKeys.feed(p.feedCode), 'trips-by-keys', { keys }] as const,
    query: async ({ signal }): Promise<Map<string, Trip | null>> => {
      const results = await Promise.allSettled(keys.map((key) => {
        const sep = key.indexOf('|')
        const revisionId = key.slice(0, sep)
        const tripId = key.slice(sep + 1)
        return optional(Gtfs.trip({ path: { feedCode: p.feedCode, tripId }, query: { revisionId }, signal }))
      }))
      // A cancelled read must reject, not resolve to a map of nulls that would be cached.
      if (signal.aborted) throw signal.reason
      return new Map(keys.map((key, i) => {
        const result = results[i]!
        return [key, result.status === 'fulfilled' ? result.value : null] as const
      }))
    },
    staleTime: Infinity,
  }
})

/** GET /feeds/{feedCode}/trips — every Trip row (all pages). */
export const tripsQuery = defineQueryOptions((p: Rev & { routeId?: string; serviceId?: string; blockId?: string; patternKey?: string }) => ({
  key: feedKeys.resource(p, 'trips', { routeId: p.routeId, serviceId: p.serviceId, blockId: p.blockId, patternKey: p.patternKey }),
  query: ({ signal }): Promise<Trip[]> =>
    fetchAllPages((offset, limit) => Gtfs.trips({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), routeId: p.routeId, serviceId: p.serviceId, blockId: p.blockId, patternKey: p.patternKey, offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/validation-finding-counts — every EntityFindingCount row (all pages). */
export const validationFindingCountsQuery = defineQueryOptions((p: Rev & { entityType: string }) => ({
  key: feedKeys.resource(p, 'validation-finding-counts', { entityType: p.entityType }),
  query: ({ signal }): Promise<EntityFindingCount[]> =>
    fetchAllPages((offset, limit) => Gtfs.validationFindingCounts({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), entityType: p.entityType, offset, limit }, signal })),
  ...cachePolicy(p),
}))

/** GET /feeds/{feedCode}/validation-findings — every ValidationFinding row (all pages). */
export const validationFindingsQuery = defineQueryOptions((p: Rev & { entityType?: string; entityId?: string }) => ({
  key: feedKeys.resource(p, 'validation-findings', { entityType: p.entityType, entityId: p.entityId }),
  query: ({ signal }): Promise<ValidationFinding[]> =>
    fetchAllPages((offset, limit) => Gtfs.validationFindings({ path: { feedCode: p.feedCode }, query: { revisionId: revisionParam(p), entityType: p.entityType, entityId: p.entityId, offset, limit }, signal })),
  ...cachePolicy(p),
}))
