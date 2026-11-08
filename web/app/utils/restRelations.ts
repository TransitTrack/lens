import { AvlFeedCatalog, Gtfs, Schedule, notFoundAsNull } from '~/api'
import type { Block, BlockTrip, Route, Shape, Stop, StopPath, Trip } from '~/api'
import { fetchAllPages } from '~/utils/restPagination'

/**
 * The REST resources are flat: relationships are ids, read through their own endpoints. These helpers
 * join them client-side into the nested shapes the views render. A detail page asks only for the stops /
 * routes it shows ([stopsByIds], [routesByIds]); the whole-revision lists ([stopsById], [routesById]) are
 * for views that really show everything, and are cached so ids can be served from them when present.
 */

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

/**
 * Reads of a revision's derived data (stop paths, a route's stops, blocks) never change once that
 * revision exists, so a read pinned to an explicit `revisionId` is kept for the life of the page and
 * shared between callers. Without a `revisionId` the read means "the active revision", which can change,
 * so it always goes to the server. The shared load ignores any one caller's abort signal — another
 * caller may be waiting on it — and is dropped again if it fails.
 */
const PINNED_MAX = 300
const pinnedCache = new Map<string, Promise<unknown>>()

function pinned<T>(s: Scope, key: string, signal: AbortSignal | undefined, load: (signal?: AbortSignal) => Promise<T>): Promise<T> {
  if (rev(s) == null) return load(signal)
  const k = `${scopeKey(s)}|${key}`
  const hit = pinnedCache.get(k)
  if (hit) return hit as Promise<T>
  const value = load()
  pinnedCache.set(k, value)
  if (pinnedCache.size > PINNED_MAX) pinnedCache.delete(pinnedCache.keys().next().value!)
  value.catch(() => pinnedCache.delete(k))
  return value
}

/** The cached value under `key`, if still fresh — without loading it. */
function fresh<T>(key: string): Promise<T> | null {
  const hit = cache.get(key)
  return hit && Date.now() - hit.at < CACHE_TTL_MS ? hit.value as Promise<T> : null
}

/** Ids per request, keeping the repeated `?stopId=` / `?routeId=` query well under URL length limits. */
const IDS_PER_REQUEST = 100

function uniqueIds(ids: Iterable<string | null | undefined>): string[] {
  return [...new Set([...ids].filter((id): id is string => !!id))]
}

function chunked<T>(items: T[], size: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size))
  return out
}

async function pick<T>(full: Promise<Map<string, T>>, ids: string[]): Promise<Map<string, T>> {
  const all = await full
  return new Map(ids.flatMap((id) => { const row = all.get(id); return row ? [[id, row] as const] : [] }))
}

const rev = (s: Scope) => (s.revisionId == null ? undefined : String(s.revisionId))
const scopeKey = (s: Scope) => `${s.feedCode}|${rev(s) ?? ''}`

/** Every stop of the revision, by stop id. */
export function stopsById(s: Scope, signal?: AbortSignal): Promise<Map<string, Stop>> {
  return cached(`stops|${scopeKey(s)}`, async () => {
    const rows = await fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: s.feedCode }, query: { revisionId: rev(s), offset, limit }, signal }))
    return new Map(rows.map((stop) => [stop.stopId, stop]))
  })
}

/** Only the given stops of the revision, by stop id. */
export async function stopsByIds(s: Scope, ids: Iterable<string | null | undefined>, signal?: AbortSignal): Promise<Map<string, Stop>> {
  const wanted = uniqueIds(ids)
  if (wanted.length === 0) return new Map()
  const full = fresh<Map<string, Stop>>(`stops|${scopeKey(s)}`)
  if (full) return pick(full, wanted)
  return pinned(s, `stops|${[...wanted].sort().join(',')}`, signal, async (sig) => {
    const pages = await Promise.all(chunked(wanted, IDS_PER_REQUEST).map((stopId) =>
      fetchAllPages((offset, limit) => Gtfs.stops({ path: { feedCode: s.feedCode }, query: { revisionId: rev(s), stopId, offset, limit }, signal: sig }))))
    return new Map(pages.flat().map((stop) => [stop.stopId, stop]))
  })
}

/** The stops a route serves (`/routes/{routeId}/stops`), by stop id. */
export function routeStops(s: Scope & { routeId: string }, signal?: AbortSignal): Promise<Map<string, Stop>> {
  return pinned(s, `routeStops|${s.routeId}`, signal, async (sig) => {
    const rows = await fetchAllPages((offset, limit) => Gtfs.routeStops({
      path: { feedCode: s.feedCode, routeId: s.routeId }, query: { revisionId: rev(s), offset, limit }, signal: sig,
    }))
    return new Map(rows.map((stop) => [stop.stopId, stop]))
  })
}

/** Only the given routes of the revision, by route id. */
export async function routesByIds(s: Scope, ids: Iterable<string | null | undefined>, signal?: AbortSignal): Promise<Map<string, Route>> {
  const wanted = uniqueIds(ids)
  if (wanted.length === 0) return new Map()
  const full = fresh<Map<string, Route>>(`routes|${scopeKey(s)}`)
  if (full) return pick(full, wanted)
  const pages = await Promise.all(chunked(wanted, IDS_PER_REQUEST).map((routeId) =>
    fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: s.feedCode }, query: { revisionId: rev(s), routeId, offset, limit }, signal }))))
  return new Map(pages.flat().map((route) => [route.routeId, route]))
}

/** Every route of the revision, by route id. */
export function routesById(s: Scope, signal?: AbortSignal): Promise<Map<string, Route>> {
  return cached(`routes|${scopeKey(s)}`, async () => {
    const rows = await fetchAllPages((offset, limit) => Gtfs.routes({ path: { feedCode: s.feedCode }, query: { revisionId: rev(s), offset, limit }, signal }))
    return new Map(rows.map((route) => [route.routeId, route]))
  })
}

/** The GTFS feed code an AVL feed is matched against. */
export async function gtfsFeedCodeOf(avlFeedCode: string, signal?: AbortSignal): Promise<string> {
  const feeds = await cached('avl-feeds', () => fetchAllPages((offset, limit) => AvlFeedCatalog.avlFeeds({ query: { offset, limit }, signal })))
  const feed = feeds.find((f) => f.code === avlFeedCode)
  if (!feed) throw new Error(`Unknown AVL feed '${avlFeedCode}'`)
  return feed.gtfsFeedCode
}

export type StopPathWithStop = StopPath & { stop: Stop | null }

/**
 * Stop paths grouped by pattern key, each carrying its stop. With a `routeId` (alone or with a
 * `patternKey`), the route's stops are read alongside the paths; for a bare pattern, only its paths'
 * stops; for the whole feed, the full stop list.
 */
export async function stopPathsByPattern(
  s: Scope & { patternKey?: string, routeId?: string },
  signal?: AbortSignal,
): Promise<Map<string, StopPathWithStop[]>> {
  const loadPaths = () => pinned(s, `stopPaths|${s.patternKey ?? ''}|${s.routeId ?? ''}`, signal, (sig) => fetchAllPages((offset, limit) => Schedule.stopPaths({
    path: { feedCode: s.feedCode },
    query: { patternKey: s.patternKey, routeId: s.routeId, revisionId: rev(s), offset, limit },
    signal: sig,
  })))
  let paths: StopPath[]
  let stops: Map<string, Stop>
  if (s.routeId) {
    // Even for one pattern of the route: the route's stops are a single plain request (a superset of
    // the pattern's), instead of a long `?stopId=` list.
    [paths, stops] = await Promise.all([loadPaths(), routeStops({ ...s, routeId: s.routeId }, signal)])
  } else if (s.patternKey) {
    paths = await loadPaths()
    stops = await stopsByIds(s, paths.map((path) => path.stopId), signal)
  } else {
    [paths, stops] = await Promise.all([loadPaths(), stopsById(s, signal)])
  }
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

/**
 * A block with just its ordered trip references (`trip: { tripId }`) — two requests, no trip or route
 * reads. Enough to show a vehicle's place in its block ("trip 3 of 8"); [blockWithTrips] is the full join.
 */
export async function blockOutline(
  s: Scope & { blockId: string, serviceId: string },
  signal?: AbortSignal,
): Promise<{ block: Block, blockTrips: Array<BlockTrip & { trip: { tripId: string } }> } | null> {
  const [block, blockTrips] = await Promise.all([
    pinned(s, `block|${s.blockId}|${s.serviceId}`, signal, (sig) => Schedule.block({
      path: { feedCode: s.feedCode, blockId: s.blockId }, query: { serviceId: s.serviceId, revisionId: rev(s) }, signal: sig,
    }).then((r) => r.data, notFoundAsNull)),
    pinned(s, `blockTrips|${s.blockId}|${s.serviceId}`, signal, (sig) => fetchAllPages((offset, limit) => Schedule.blockTrips({
      path: { feedCode: s.feedCode },
      query: { blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s), offset, limit },
      signal: sig,
    }))),
  ])
  return block ? { block, blockTrips: blockTrips.map((bt) => ({ ...bt, trip: { tripId: bt.tripId } })) } : null
}

/** A block, its trips in scheduled order, and each trip's route. */
export async function blockWithTrips(
  s: Scope & { blockId: string, serviceId: string },
  signal?: AbortSignal,
): Promise<BlockWithTrips | null> {
  const [block, blockTrips, trips] = await Promise.all([
    Schedule.block({
      path: { feedCode: s.feedCode, blockId: s.blockId }, query: { serviceId: s.serviceId, revisionId: rev(s) }, signal,
    }).then((r) => r.data, notFoundAsNull),
    fetchAllPages((offset, limit) => Schedule.blockTrips({
      path: { feedCode: s.feedCode },
      query: { blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s), offset, limit },
      signal,
    })),
    fetchAllPages((offset, limit) => Gtfs.trips({
      path: { feedCode: s.feedCode },
      query: { blockId: s.blockId, serviceId: s.serviceId, revisionId: rev(s), offset, limit },
      signal,
    })),
  ])
  if (!block) return null
  const routes = await routesByIds(s, trips.map((trip) => trip.routeId), signal)
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
  return await Gtfs.shape({ path: { feedCode: s.feedCode, shapeId }, query: { revisionId: rev(s) }, signal })
    .then((r) => r.data, notFoundAsNull)
}
