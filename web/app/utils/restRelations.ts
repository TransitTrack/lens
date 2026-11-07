import { AvlFeedCatalogApi, GTFSApi, ScheduleApi } from '~~/generated/rest'
import type { Block, BlockTrip, Route, Shape, Stop, StopPath, Trip } from '~~/generated/rest'
import { fetchAllPages } from '~/utils/restPagination'

/**
 * The REST resources are flat: relationships are ids, read through their own endpoints. These helpers
 * join them client-side into the nested shapes the views render, sharing one cached read of the big
 * reference lists (stops, routes) so a detail page costs a handful of requests.
 */
const gtfs = new GTFSApi()
const schedule = new ScheduleApi()
const avlCatalog = new AvlFeedCatalogApi()

export interface Scope {
  feedCode: string
  revisionId?: string | number | null
}

const CACHE_TTL_MS = 60_000
const cache = new Map<string, { at: number, value: Promise<unknown> }>()

function cached<T>(key: string, load: () => Promise<T>): Promise<T> {
  const hit = cache.get(key)
  if (hit && Date.now() - hit.at < CACHE_TTL_MS) return hit.value as Promise<T>
  const value = load()
  cache.set(key, { at: Date.now(), value })
  value.catch(() => cache.delete(key))
  return value
}

const rev = (s: Scope) => (s.revisionId == null ? undefined : String(s.revisionId))
const scopeKey = (s: Scope) => `${s.feedCode}|${rev(s) ?? ''}`

/** Every stop of the revision, by stop id. */
export function stopsById(s: Scope, signal?: AbortSignal): Promise<Map<string, Stop>> {
  return cached(`stops|${scopeKey(s)}`, async () => {
    const rows = await fetchAllPages((offset, limit) => gtfs.stopsRaw({ feedCode: s.feedCode, revisionId: rev(s), offset, limit }, { signal }))
    return new Map(rows.map((stop) => [stop.stopId, stop]))
  })
}

/** Every route of the revision, by route id. */
export function routesById(s: Scope, signal?: AbortSignal): Promise<Map<string, Route>> {
  return cached(`routes|${scopeKey(s)}`, async () => {
    const rows = await fetchAllPages((offset, limit) => gtfs.routesRaw({ feedCode: s.feedCode, revisionId: rev(s), offset, limit }, { signal }))
    return new Map(rows.map((route) => [route.routeId, route]))
  })
}

/** The GTFS feed code an AVL feed is matched against. */
export async function gtfsFeedCodeOf(avlFeedCode: string, signal?: AbortSignal): Promise<string> {
  const feeds = await cached('avl-feeds', () => fetchAllPages((offset, limit) => avlCatalog.avlFeedsRaw({ offset, limit }, { signal })))
  const feed = feeds.find((f) => f.code === avlFeedCode)
  if (!feed) throw new Error(`Unknown AVL feed '${avlFeedCode}'`)
  return feed.gtfsFeedCode
}

export type StopPathWithStop = StopPath & { stop: Stop | null }

/** Stop paths grouped by pattern key, each carrying its stop. */
export async function stopPathsByPattern(
  s: Scope & { patternKey?: string, routeId?: string },
  signal?: AbortSignal,
): Promise<Map<string, StopPathWithStop[]>> {
  const [paths, stops] = await Promise.all([
    fetchAllPages((offset, limit) => schedule.stopPathsRaw({
      feedCode: s.feedCode, patternKey: s.patternKey, routeId: s.routeId, revisionId: rev(s), offset, limit,
    }, { signal })),
    stopsById(s, signal),
  ])
  const grouped = new Map<string, StopPathWithStop[]>()
  for (const path of paths) {
    const list = grouped.get(path.patternKey) ?? []
    list.push({ ...path, stop: stops.get(path.stopId) ?? null })
    grouped.set(path.patternKey, list)
  }
  return grouped
}

/** A trip with its route (or null when the route is unknown). */
export function withRoute<T extends { routeId: string }>(trip: T, routes: Map<string, Route>): T & { route: Route | null } {
  return { ...trip, route: routes.get(trip.routeId) ?? null }
}

export interface BlockWithTrips {
  block: Block
  blockTrips: Array<BlockTrip & { trip: Trip & { route: Route | null } }>
}

/** A block, its trips in scheduled order, and each trip's route. */
export async function blockWithTrips(
  s: Scope & { blockId: string, serviceId: string },
  signal?: AbortSignal,
): Promise<BlockWithTrips | null> {
  const [block, blockTrips, trips, routes] = await Promise.all([
    schedule.block({ feedCode: s.feedCode, blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s) }, { signal }).catch(notFoundAsNull),
    fetchAllPages((offset, limit) => schedule.blockTripsRaw({
      feedCode: s.feedCode, blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s), offset, limit,
    }, { signal })),
    fetchAllPages((offset, limit) => gtfs.tripsRaw({
      feedCode: s.feedCode, blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s), offset, limit,
    }, { signal })),
    routesById(s, signal),
  ])
  if (!block) return null
  const tripById = new Map(trips.map((trip) => [trip.tripId, trip]))
  return {
    block,
    blockTrips: blockTrips.flatMap((bt) => {
      const trip = tripById.get(bt.tripId)
      return trip ? [{ ...bt, trip: withRoute(trip, routes) }] : []
    }),
  }
}

export async function shapeOf(s: Scope, shapeId: string | null | undefined, signal?: AbortSignal): Promise<Shape | null> {
  if (!shapeId) return null
  return await gtfs.shape({ feedCode: s.feedCode, shapeId, revisionId: rev(s) }, { signal }).catch(notFoundAsNull)
}

/** Resolves a failed `404` request to `null`; anything else is rethrown. */
export function notFoundAsNull(error: unknown): null {
  if (error && typeof error === 'object' && 'response' in error && (error as { response: Response }).response?.status === 404) return null
  throw error
}
