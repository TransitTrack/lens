import { AVLApi, GTFSApi, PredictionsApi, ScheduleApi, ResponseError } from '~~/generated/rest'
import type { AvlReport, AvlReportMatch, PredictionAlgorithm  } from '~~/generated/rest'
import type {
  VehicleDetailQuery, VehicleDetailQueryVariables, StopBoardQuery, StopBoardQueryVariables,
  HeadwayInfoQuery, HeadwayInfoQueryVariables, HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables,
  AvlLogQuery, AvlLogQueryVariables, AvlTrailQuery, AvlTrailQueryVariables,
  AvlReportCatalogQuery, AvlReportCatalogQueryVariables, AvlReportPageQuery, AvlReportPageQueryVariables,
} from '~/types/restViews'
import { fetchAllPages } from '~/utils/restPagination'
import { blockWithTrips, gtfsFeedCodeOf, routesById, shapeOf, stopPathsByPattern, stopsById, withRoute } from '~/utils/restRelations'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

const avl = new AVLApi()
const predictions = new PredictionsApi()
const schedule = new ScheduleApi()
const gtfs = new GTFSApi()
async function optional<T>(load: Promise<T>): Promise<T | null> {
  try { return await load } catch (error) {
    if (error instanceof ResponseError && error.response.status === 404) return null
    throw error
  }
}

/** The vehicle with its trip, route, shape, pattern (with stops), block and current stop joined in. */
async function vehicleWithRelations(v: VehicleDetailQueryVariables, signal: AbortSignal) {
  const vehicle = await optional(avl.vehicle(v, { signal }))
  if (!vehicle) return null
  const unmatched = { ...vehicle, trip: null, pattern: null, block: null, currentStop: null }
  if (!vehicle.revisionId) return unmatched
  const scope = { feedCode: await gtfsFeedCodeOf(v.feedCode, signal), revisionId: vehicle.revisionId }
  const trip = vehicle.tripId ? await optional(gtfs.trip({ ...scope, revisionId: vehicle.revisionId ?? undefined, tripId: vehicle.tripId }, { signal })) : null
  const [route, shape, pattern, block, stops] = await Promise.all([
    trip ? optional(gtfs.route({ feedCode: scope.feedCode, routeId: trip.routeId, revisionId: vehicle.revisionId ?? undefined }, { signal })) : null,
    trip ? shapeOf(scope, trip.shapeId, signal) : null,
    trip?.patternKey ? patternWithStops(scope, trip.patternKey, signal) : null,
    trip?.blockId ? blockWithTrips({ ...scope, blockId: trip.blockId, serviceId: trip.serviceId }, signal) : null,
    stopsById(scope, signal),
  ])
  return {
    ...vehicle,
    trip: trip ? { ...trip, route, shape } : null,
    pattern,
    block: block ? { ...block.block, blockTrips: block.blockTrips } : null,
    currentStop: vehicle.currentStopId ? stops.get(vehicle.currentStopId) ?? null : null,
  }
}

async function patternWithStops(scope: { feedCode: string, revisionId?: string | null }, patternKey: string, signal: AbortSignal) {
  const [pattern, stopPaths] = await Promise.all([
    optional(schedule.tripPattern({ feedCode: scope.feedCode, patternKey, revisionId: scope.revisionId ?? undefined }, { signal })),
    stopPathsByPattern({ ...scope, patternKey }, signal),
  ])
  return pattern ? { ...pattern, stopPaths: stopPaths.get(patternKey) ?? [] } : null
}

export function useVehicleDetailQuery(options: () => RestQueryOptions<VehicleDetailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<VehicleDetailQuery> => {
    const [vehicle, predictionRows, predictionAccuracy] = await Promise.all([
      vehicleWithRelations(v, signal),
      fetchAllPages((offset, limit) => predictions.vehiclePredictionsRaw({ ...v, offset, limit }, { signal })),
      fetchAllPages((offset, limit) => predictions.predictionAccuracyRaw({ feedCode: v.feedCode, offset, limit }, { signal })),
    ])
    const stops = vehicle?.revisionId
      ? await stopsById({ feedCode: await gtfsFeedCodeOf(v.feedCode, signal), revisionId: vehicle.revisionId }, signal)
      : new Map()
    const vehiclePredictions = predictionRows.map((p) => ({ ...p, stop: stops.get(p.stopId) ?? null }))
    return { vehicle, vehiclePredictions, predictionAccuracy } as unknown as VehicleDetailQuery
  })
}

export function useStopBoardQuery(options: () => RestQueryOptions<StopBoardQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<StopBoardQuery> => ({
    stopPredictions: await fetchAllPages((offset, limit) => predictions.stopPredictionsRaw({
      ...v, routeId: v.routeId ?? undefined, directionId: v.directionId ?? undefined,
      algorithm: (v.algorithm ?? undefined) as PredictionAlgorithm | undefined, offset, limit,
    }, { signal })) as StopBoardQuery['stopPredictions'],
  }))
}

export function useHeadwayInfoQuery(options: () => RestQueryOptions<HeadwayInfoQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<HeadwayInfoQuery> => ({
    headway: await predictions.headway({ ...v, directionId: v.directionId ?? undefined,
      algorithm: (v.algorithm ?? undefined) as PredictionAlgorithm | undefined }, { signal }) as HeadwayInfoQuery['headway'],
  }))
}

async function routePatternsWithStops(v: HeadwayRoutePatternsQueryVariables, signal: AbortSignal) {
  const [patterns, stopPaths] = await Promise.all([
    fetchAllPages((offset, limit) => schedule.tripPatternsRaw({ ...v, offset, limit }, { signal })),
    stopPathsByPattern(v, signal),
  ])
  return patterns.map((p) => ({ ...p, stopPaths: stopPaths.get(p.patternKey) ?? [] }))
}

export function useHeadwayRoutePatternsQuery(options: () => RestQueryOptions<HeadwayRoutePatternsQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<HeadwayRoutePatternsQuery> => ({
    tripPatterns: await routePatternsWithStops(v, signal) as HeadwayRoutePatternsQuery['tripPatterns'],
  }))
}

/**
 * Joins each report's matched trip (and, with [withRoute], its route) onto it. Trips are read once per
 * distinct trip id of the report's revision.
 */
async function reportsWithTrips<T extends { match?: AvlReportMatch | null }>(
  avlFeedCode: string, reports: T[], includeRoute: boolean, signal: AbortSignal,
) {
  const matches = reports.flatMap((r) => (r.match ? [r.match] : []))
  if (matches.length === 0) return reports.map((r) => ({ ...r, match: null }))
  const feedCode = await gtfsFeedCodeOf(avlFeedCode, signal)
  const keys = [...new Map(matches.map((m) => [`${m.revisionId}|${m.tripId}`, m])).values()]
  const trips = new Map(await Promise.all(keys.map(async (m) => [
    `${m.revisionId}|${m.tripId}`,
    await optional(gtfs.trip({ feedCode, tripId: m.tripId, revisionId: m.revisionId }, { signal })),
  ] as const)))
  const routes = includeRoute
    ? new Map(await Promise.all([...new Set(matches.map((m) => m.revisionId))].map(async (revisionId) =>
      [revisionId, await routesById({ feedCode, revisionId }, signal)] as const)))
    : null
  return reports.map((r) => {
    if (!r.match) return { ...r, match: null }
    const trip = trips.get(`${r.match.revisionId}|${r.match.tripId}`) ?? null
    const joined = trip && routes ? withRoute(trip, routes.get(r.match.revisionId)!) : trip
    return { ...r, match: { ...r.match, trip: joined } }
  })
}

export function useAvlLogQuery(options: () => RestQueryOptions<AvlLogQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<AvlLogQuery> => {
    const reports = await avl.avlReports({ ...v, limit: v.limit ?? undefined }, { signal })
    return { avlReports: await reportsWithTrips(v.feedCode, reports, true, signal) } as unknown as AvlLogQuery
  })
}

export function useAvlTrailQuery(options: () => RestQueryOptions<AvlTrailQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<AvlTrailQuery> => {
    const reports = await avl.avlReports({ ...v, limit: v.limit ?? undefined }, { signal })
    return { avlReports: await reportsWithTrips(v.feedCode, reports, false, signal) } as unknown as AvlTrailQuery
  })
}

export function useAvlReportCatalogQuery(options: () => RestQueryOptions<AvlReportCatalogQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<AvlReportCatalogQuery> => ({
    avlReportCatalog: await avl.avlReportCatalog(v, { signal }),
  }))
}

export function useAvlReportPageQuery(options: () => RestQueryOptions<AvlReportPageQueryVariables>) {
  return useRestQuery(options, async (v, signal): Promise<AvlReportPageQuery> => {
    const page = await avl.avlReportPage({ ...v, page: v.page ?? undefined, pageSize: v.pageSize ?? undefined }, { signal })
    const items: AvlReport[] = page.items
    return { avlReportPage: { ...page, items: await reportsWithTrips(v.feedCode, items, false, signal) } } as unknown as AvlReportPageQuery
  })
}
