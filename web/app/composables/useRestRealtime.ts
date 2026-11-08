import { Avl, Gtfs, Predictions, Schedule, optional } from '~/api'
import type { AvlReport, AvlReportMatch, PredictionAlgorithm, Stop } from '~/api'
import type {
  PredictionAccuracy, VehicleDetailQuery, VehicleDetailQueryVariables, StopBoardQuery, StopBoardQueryVariables,
  HeadwayInfoQuery, HeadwayInfoQueryVariables, HeadwayRoutePatternsQuery, HeadwayRoutePatternsQueryVariables,
  AvlLogQuery, AvlLogQueryVariables, AvlTrailQuery, AvlTrailQueryVariables, VehicleTrailQuery, VehicleTrailQueryVariables,
  AvlReportCatalogQuery, AvlReportCatalogQueryVariables, AvlReportPageQuery, AvlReportPageQueryVariables,
} from '~/types/restViews'
import { geometryLine } from '~/utils/gtfs'
import { fetchAllPages } from '~/utils/restPagination'
import { blockOutline, gtfsFeedCodeOf, routesByIds, shapeOf, stopPathsByPattern, stopsByIds, withRoute } from '~/utils/restRelations'
import { useRestQuery, type RestQueryOptions } from './useRestQuery'

/** The vehicle with its trip, route, shape, pattern (with stops), block and current stop joined in. */
async function vehicleWithRelations(v: VehicleDetailQueryVariables, signal: AbortSignal) {
  const vehicle = await optional(Avl.vehicle({ path: { feedCode: v.feedCode, vehicleId: v.vehicleId }, signal }))
  if (!vehicle) return null
  const unmatched = { ...vehicle, trip: null, pattern: null, block: null }
  if (!vehicle.revisionId) return unmatched
  const scope = { feedCode: await gtfsFeedCodeOf(v.feedCode, signal), revisionId: vehicle.revisionId }
  const trip = vehicle.tripId ? await optional(Gtfs.trip({ path: { feedCode: scope.feedCode, tripId: vehicle.tripId }, query: { revisionId: vehicle.revisionId }, signal })) : null
  // A vehicle runs one route at a time, so its route is read by id; the block is only an outline
  // (its trip order, for "trip N of M") — the vehicle views never show the block's other trips.
  const [route, pattern, block] = await Promise.all([
    trip ? optional(Gtfs.route({ path: { feedCode: scope.feedCode, routeId: trip.routeId }, query: { revisionId: vehicle.revisionId }, signal })) : null,
    trip?.patternKey ? patternWithStops(scope, trip.patternKey, trip.routeId, signal) : null,
    trip?.blockId ? blockOutline({ ...scope, blockId: trip.blockId, serviceId: trip.serviceId }, signal) : null,
  ])
  // The GTFS shape is only the fallback for the route line (see `routeLine`): read it just when the
  // pattern has no usable geometry of its own.
  const shape = trip && geometryLine(pattern?.geometry).length < 2 ? await shapeOf(scope, trip.shapeId, signal) : null
  return {
    ...vehicle,
    trip: trip ? { ...trip, route, shape } : null,
    pattern,
    block: block ? { ...block.block, blockTrips: block.blockTrips } : null,
  }
}

async function patternWithStops(scope: { feedCode: string, revisionId?: string | null }, patternKey: string, routeId: string, signal: AbortSignal) {
  const [pattern, stopPaths] = await Promise.all([
    optional(Schedule.tripPattern({ path: { feedCode: scope.feedCode, patternKey }, query: { revisionId: scope.revisionId ?? undefined }, signal })),
    stopPathsByPattern({ ...scope, patternKey, routeId }, signal),
  ])
  return pattern ? { ...pattern, stopPaths: stopPaths.get(patternKey) ?? [] } : null
}

export function useVehicleDetailQuery(options: () => RestQueryOptions<VehicleDetailQueryVariables>) {
  return useRestQuery('useVehicleDetailQuery', options, async (v, signal): Promise<VehicleDetailQuery> => {
    const [vehicle, predictionRows] = await Promise.all([
      vehicleWithRelations(v, signal),
      fetchAllPages((offset, limit) => Predictions.vehiclePredictions({ path: { feedCode: v.feedCode, vehicleId: v.vehicleId }, query: { algorithm: (v.algorithm ?? undefined) as PredictionAlgorithm | undefined, offset, limit }, signal })),
    ])
    // The vehicle's current stop and the predicted ones sit on its pattern, whose stop paths already
    // carry their stops: look them up there, and ask the server only for any that aren't.
    const stops = new Map<string, Stop>()
    for (const path of vehicle?.pattern?.stopPaths ?? []) if (path.stop) stops.set(path.stopId, path.stop)
    if (vehicle?.revisionId) {
      const missing = [vehicle.currentStopId, ...predictionRows.map((p) => p.stopId)].filter((id): id is string => !!id && !stops.has(id))
      if (missing.length) {
        const found = await stopsByIds({ feedCode: await gtfsFeedCodeOf(v.feedCode, signal), revisionId: vehicle.revisionId }, missing, signal)
        for (const [id, stop] of found) stops.set(id, stop)
      }
    }
    const vehiclePredictions = predictionRows.map((p) => ({ ...p, stop: stops.get(p.stopId) ?? null }))
    const withStop = vehicle ? { ...vehicle, currentStop: vehicle.currentStopId ? stops.get(vehicle.currentStopId) ?? null : null } : null
    return { vehicle: withStop, vehiclePredictions } as unknown as VehicleDetailQuery
  })
}

/**
 * The feed's prediction accuracy (an aggregate over the last days, identical for every vehicle), kept
 * apart from [useVehicleDetailQuery] so it isn't recomputed on each vehicle poll. Poll it rarely.
 */
export function useFeedPredictionAccuracyQuery(options: () => RestQueryOptions<{ feedCode: string }>) {
  return useRestQuery('useFeedPredictionAccuracyQuery', options, async ({ feedCode }, signal): Promise<{ predictionAccuracy: PredictionAccuracy }> => ({
    predictionAccuracy: await fetchAllPages((offset, limit) => Predictions.predictionAccuracy({ path: { feedCode }, query: { offset, limit }, signal })) as unknown as PredictionAccuracy,
  }))
}

export function useStopBoardQuery(options: () => RestQueryOptions<StopBoardQueryVariables>) {
  return useRestQuery('useStopBoardQuery', options, async (v, signal): Promise<StopBoardQuery> => ({
    stopPredictions: await fetchAllPages((offset, limit) => Predictions.stopPredictions({
      path: { feedCode: v.feedCode, stopId: v.stopId },
      query: {
        routeId: v.routeId ?? undefined, directionId: v.directionId ?? undefined,
        algorithm: (v.algorithm ?? undefined) as PredictionAlgorithm | undefined, offset, limit,
      },
      signal,
    })) as StopBoardQuery['stopPredictions'],
  }))
}

export function useHeadwayInfoQuery(options: () => RestQueryOptions<HeadwayInfoQueryVariables>) {
  return useRestQuery('useHeadwayInfoQuery', options, async (v, signal): Promise<HeadwayInfoQuery> => ({
    headway: (await Predictions.headway({
      path: { feedCode: v.feedCode, stopId: v.stopId },
      query: {
        routeId: v.routeId, directionId: v.directionId ?? undefined,
        algorithm: (v.algorithm ?? undefined) as PredictionAlgorithm | undefined,
      },
      signal,
    })).data as HeadwayInfoQuery['headway'],
  }))
}

async function routePatternsWithStops(v: HeadwayRoutePatternsQueryVariables, signal: AbortSignal) {
  const [patterns, stopPaths] = await Promise.all([
    fetchAllPages((offset, limit) => Schedule.tripPatterns({ path: { feedCode: v.feedCode }, query: { routeId: v.routeId, offset, limit }, signal })),
    stopPathsByPattern(v, signal),
  ])
  return patterns.map((p) => ({ ...p, stopPaths: stopPaths.get(p.patternKey) ?? [] }))
}

export function useHeadwayRoutePatternsQuery(options: () => RestQueryOptions<HeadwayRoutePatternsQueryVariables>) {
  return useRestQuery('useHeadwayRoutePatternsQuery', options, async (v, signal): Promise<HeadwayRoutePatternsQuery> => ({
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
    await optional(Gtfs.trip({ path: { feedCode, tripId: m.tripId }, query: { revisionId: m.revisionId }, signal })),
  ] as const)))
  const routes = includeRoute
    ? new Map(await Promise.all([...new Set(matches.map((m) => m.revisionId))].map(async (revisionId) => {
      const routeIds = keys.filter((m) => m.revisionId === revisionId).map((m) => trips.get(`${m.revisionId}|${m.tripId}`)?.routeId)
      return [revisionId, await routesByIds({ feedCode, revisionId }, routeIds, signal)] as const
    })))
    : null
  return reports.map((r) => {
    if (!r.match) return { ...r, match: null }
    const trip = trips.get(`${r.match.revisionId}|${r.match.tripId}`) ?? null
    const joined = trip && routes ? withRoute(trip, routes.get(r.match.revisionId)!) : trip
    return { ...r, match: { ...r.match, trip: joined } }
  })
}

export function useAvlLogQuery(options: () => RestQueryOptions<AvlLogQueryVariables>) {
  return useRestQuery('useAvlLogQuery', options, async (v, signal): Promise<AvlLogQuery> => {
    const reports = (await Avl.avlReports({ path: { feedCode: v.feedCode, vehicleId: v.vehicleId }, query: { limit: v.limit ?? undefined }, signal })).data
    return { avlReports: await reportsWithTrips(v.feedCode, reports, true, signal) } as unknown as AvlLogQuery
  })
}

/**
 * A vehicle's recent movement, summarised by the backend: its track, report cadence, newest status and
 * the speeds reported along the trip it is on — instead of downloading and filtering raw reports.
 */
export function useVehicleTrailQuery(options: () => RestQueryOptions<VehicleTrailQueryVariables>) {
  return useRestQuery('useVehicleTrailQuery', options, async (v, signal): Promise<VehicleTrailQuery> => ({
    vehicleTrail: (await Avl.vehicleTrail({ path: { feedCode: v.feedCode, vehicleId: v.vehicleId }, query: { limit: v.limit ?? undefined }, signal })).data,
  }))
}

/** The newest few raw AVL reports of a vehicle (newest first), for short-range estimates. */
export function useRecentAvlReportsQuery(options: () => RestQueryOptions<AvlTrailQueryVariables>) {
  return useRestQuery('useRecentAvlReportsQuery', options, async (v, signal): Promise<AvlTrailQuery> => ({
    avlReports: (await Avl.avlReports({ path: { feedCode: v.feedCode, vehicleId: v.vehicleId }, query: { limit: v.limit ?? undefined }, signal })).data,
  }) as unknown as AvlTrailQuery)
}

export function useAvlReportCatalogQuery(options: () => RestQueryOptions<AvlReportCatalogQueryVariables>) {
  return useRestQuery('useAvlReportCatalogQuery', options, async (v, signal): Promise<AvlReportCatalogQuery> => ({
    avlReportCatalog: (await Avl.avlReportCatalog({ path: { feedCode: v.feedCode }, signal })).data,
  }))
}

export function useAvlReportPageQuery(options: () => RestQueryOptions<AvlReportPageQueryVariables>) {
  return useRestQuery('useAvlReportPageQuery', options, async (v, signal): Promise<AvlReportPageQuery> => {
    const { data: page } = await Avl.avlReportPage({
      path: { feedCode: v.feedCode, vehicleId: v.vehicleId },
      query: { date: v.date, startTime: v.startTime, endTime: v.endTime, page: v.page ?? undefined, pageSize: v.pageSize ?? undefined },
      signal,
    })
    const items: AvlReport[] = page.items
    return { avlReportPage: { ...page, items: await reportsWithTrips(v.feedCode, items, false, signal) } } as unknown as AvlReportPageQuery
  })
}
