import type { Block, BlockTrip, Route, Shape, Stop, StopPath, StopPrediction, Trip, TripPattern, Vehicle } from '~/api'
import type { VehicleDetailQuery } from '~/types/restViews'
import { geometryLine } from '~/utils/gtfs'
import { groupStopPaths, type StopPathWithStop } from './stopPaths'

/** A vehicle with its relations and its stop predictions, as the vehicle views render them. */
export type VehicleDetail = Pick<VehicleDetailQuery, 'vehicle' | 'vehiclePredictions'>

export type VehiclePattern = TripPattern & { stopPaths: StopPathWithStop[] }

/**
 * The trip's pattern with its own stop paths, each carrying its stop. `stopPaths` / `routeStops` are the
 * reads of the trip's route (`stopPathsQuery({ routeId, patternKey })`, `routeStopsQuery({ routeId })`).
 */
export function vehiclePattern(
  trip: Pick<Trip, 'patternKey'>,
  pattern: TripPattern | null | undefined,
  stopPaths: StopPath[],
  routeStops: Stop[],
): VehiclePattern | null {
  if (!pattern || !trip.patternKey) return null
  return { ...pattern, stopPaths: groupStopPaths(stopPaths, routeStops).get(trip.patternKey) ?? [] }
}

/** The GTFS shape is only the fallback for the route line: needed when the pattern has no usable geometry. */
export function needsShape(pattern: Pick<TripPattern, 'geometry'> | null | undefined): boolean {
  return geometryLine(pattern?.geometry).length < 2
}

function patternStops(pattern: VehiclePattern | null | undefined): Map<string, Stop> {
  const stops = new Map<string, Stop>()
  for (const path of pattern?.stopPaths ?? []) if (path.stop) stops.set(path.stopId, path.stop)
  return stops
}

/**
 * The current and predicted stops that are not on the vehicle's pattern (whose stop paths already carry
 * their stops) — the only ones to read by id. None for an unmatched vehicle.
 */
export function missingStopIds(
  vehicle: Vehicle | null | undefined,
  predictions: StopPrediction[],
  pattern: VehiclePattern | null | undefined,
): string[] {
  if (!vehicle?.revisionId) return []
  const known = patternStops(pattern)
  const ids = [vehicle.currentStopId, ...predictions.map((p) => p.stopId)]
  return [...new Set(ids.filter((id): id is string => !!id && !known.has(id)))]
}

export interface VehicleDetailParts {
  vehicle: Vehicle | null | undefined
  predictions: StopPrediction[]
  trip?: Trip | null
  route?: Route | null
  shape?: Shape | null
  pattern?: TripPattern | null
  stopPaths?: StopPath[]
  routeStops?: Stop[]
  block?: Block | null
  blockTrips?: BlockTrip[]
  /** The stops read by id (see `missingStopIds`). */
  stops?: Stop[]
}

/**
 * Joins the vehicle with its trip (and the trip's route and fallback shape), its pattern with stops, its
 * block outline (ordered trip references, for "trip N of M") and its current stop, and gives each
 * prediction its stop. An unmatched vehicle (no revision) has no trip, pattern or block.
 */
export function vehicleDetail(parts: VehicleDetailParts): VehicleDetail {
  const { vehicle, predictions, trip } = parts
  const matched = !!vehicle?.revisionId
  const pattern = matched && trip ? vehiclePattern(trip, parts.pattern, parts.stopPaths ?? [], parts.routeStops ?? []) : null
  const stops = patternStops(pattern)
  if (matched) for (const stop of parts.stops ?? []) stops.set(stop.stopId, stop)
  const vehiclePredictions = predictions.map((p) => ({ ...p, stop: stops.get(p.stopId) ?? null }))
  if (!vehicle) return { vehicle: null, vehiclePredictions } as unknown as VehicleDetail

  const block = matched && trip?.blockId && parts.block
    ? { ...parts.block, blockTrips: (parts.blockTrips ?? []).map((bt) => ({ ...bt, trip: { tripId: bt.tripId } })) }
    : null
  const joined = {
    ...vehicle,
    trip: matched && trip ? { ...trip, route: parts.route ?? null, shape: parts.shape ?? null } : null,
    pattern,
    block,
    currentStop: vehicle.currentStopId ? stops.get(vehicle.currentStopId) ?? null : null,
  }
  return { vehicle: joined, vehiclePredictions } as unknown as VehicleDetail
}
