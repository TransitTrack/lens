import type { Ref } from 'vue'
import type { AvlOccupancyStatus, Block, BlockTrip, PredictionAlgorithm, Route, Shape, Stop, StopPath, StopPrediction, Trip, TripPattern, Vehicle } from '~/api'
import { geometryLine } from '~/utils/gtfs'
import { groupStopPaths, type StopPathWithStop } from './stopPaths'

/** A vehicle with its relations and its stop predictions, as the vehicle views render them. */
export type VehicleDetail = {
  vehicle: { vehicleId: string, label: string | null, reportTs: string, bearing: number | null, speedMps: number | null, occupancyStatus: AvlOccupancyStatus | null, matched: boolean, stale: boolean, scheduleAdherenceSec: number | null, stopPathIndex: number | null, distanceAlongTripM: number | null, position: { lat: number, lon: number }, snappedPosition: { lat: number, lon: number } | null, currentStop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null, block: { blockId: string, serviceId: string, startTimeSec: number, endTimeSec: number, blockTrips: Array<{ listIndex: number, trip: { tripId: string, tripHeadsign: string | null } }> } | null, trip: { tripId: string, tripHeadsign: string | null, directionId: number | null, routeId: string, startTimeSec: number | null, endTimeSec: number | null, route: { routeShortName: string | null, routeLongName: string | null, routeColor: string | null, routeTextColor: string | null } | null, shape: { geometry: string | null } | null } | null, pattern: { patternKey: string, lengthM: number | null, geometry: string | null, stopPaths: Array<{ stopPathIndex: number, stopId: string, waitStop: boolean, lengthM: number, scheduleAdherenceStop: boolean, stop: { stopId: string, stopName: string | null, stopLat: number | null, stopLon: number | null } | null }> } | null } | null
  vehiclePredictions: Array<{ stopPathIndex: number, scheduledArrival: string | null, scheduledDeparture: string | null, predictedArrival: string | null, predictedDeparture: string | null, actualArrival: string | null, actualDeparture: string | null, actualReportGapSec: number | null, actualReliable: boolean | null, sourceReportTs: string | null, algorithm: PredictionAlgorithm | null, confidenceSec: number | null, stop: { stopId: string, stopName: string | null } | null }>
}

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

/** One read of a page's query chain: whether the page enabled it, and its `useQuery` state refs. */
export interface ChainRead {
  enabled: boolean
  q: {
    data: Readonly<Ref<unknown>>
    isPlaceholderData: Readonly<Ref<boolean>>
    error: Readonly<Ref<Error | null>>
    isLoading: Readonly<Ref<boolean>>
  }
}

/** An enabled read still waiting: no data of its own key yet (none, or only the previous key's placeholder) and no error. */
export function readPending({ enabled, q }: ChainRead): boolean {
  return enabled && !q.error.value && (q.data.value === undefined || q.isPlaceholderData.value)
}

/** Some enabled read of the chain has not answered yet (see `readPending`). */
export function chainPending(reads: ChainRead[]): boolean {
  return reads.some(readPending)
}

/** Some enabled read of the chain is fetching (first load or a poll). */
export function chainLoading(reads: ChainRead[]): boolean {
  return reads.some(({ enabled, q }) => enabled && q.isLoading.value)
}

/** The first error among the chain's enabled reads. */
export function chainError(reads: ChainRead[]): Error | null {
  return reads.find(({ enabled, q }) => enabled && q.error.value)?.q.error.value ?? null
}
