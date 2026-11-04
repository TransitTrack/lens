import type { VehicleDetailQuery } from '../../generated/graphql'
import { formatClock } from './clock'

type Vehicle = NonNullable<VehicleDetailQuery['vehicle']>
type Prediction = VehicleDetailQuery['vehiclePredictions'][number]
type Accuracy = VehicleDetailQuery['predictionAccuracy'][number]

/**
 * The route line, as an ordered list of [lon, lat] pairs. Prefers the schedule
 * pattern's per-segment `pathGeometry` (already [lon, lat]); falls back to the
 * GTFS shape, then to a straight line through the ordered stops.
 */
export function routeLine(vehicle: Vehicle): LngLat[] {
  const paths = vehicle.pattern?.stopPaths ?? []
  const fromPattern: LngLat[] = []
  for (const p of [...paths].sort((a, b) => a.stopPathIndex - b.stopPathIndex)) {
    const geom = p.pathGeometry
    if (Array.isArray(geom)) {
      for (const pt of geom) {
        if (
          Array.isArray(pt) &&
          pt.length >= 2 &&
          typeof pt[0] === 'number' &&
          typeof pt[1] === 'number'
        ) {
          fromPattern.push([pt[0], pt[1]])
        }
      }
    }
  }
  if (fromPattern.length >= 2) return dedupe(fromPattern)

  const shapePts = vehicle.trip?.shape?.points ?? []
  const fromShape: LngLat[] = shapePts
    .filter((pt): pt is { lat: number; lon: number } => pt.lat != null && pt.lon != null)
    .map((pt) => [pt.lon, pt.lat])
  if (fromShape.length >= 2) return dedupe(fromShape)

  return dedupe(stopCoords(vehicle))
}

function stopCoords(vehicle: Vehicle): LngLat[] {
  return (vehicle.pattern?.stopPaths ?? [])
    .slice()
    .sort((a, b) => a.stopPathIndex - b.stopPathIndex)
    .map((p) => p.stop)
    .filter((s): s is NonNullable<typeof s> => !!s && s.stopLat != null && s.stopLon != null)
    .map((s) => [s.stopLon as number, s.stopLat as number])
}

function dedupe(coords: LngLat[]): LngLat[] {
  return coords.filter(
    (c, i) => i === 0 || c[0] !== coords[i - 1]![0] || c[1] !== coords[i - 1]![1],
  )
}

export type StopState = 'passed' | 'current' | 'upcoming'

export interface StopFeature {
  type: 'Feature'
  geometry: { type: 'Point'; coordinates: LngLat }
  properties: { name: string; label?: string; state: StopState }
}

/**
 * Stops for a selected vehicle's map overlay. When a prediction algorithm is
 * available, upcoming stops also carry its ETA as a second label line.
 */
export function stopFeatureCollection(
  vehicle: Vehicle,
  predictions: Prediction[] = [],
  algorithm?: string,
  timeZone?: string,
  locale?: string,
) {
  const nextIndex = vehicle.stopPathIndex ?? -1
  const predictionsByIndex = new Map<number, Prediction>()
  for (const prediction of predictions) {
    if (prediction.algorithm === algorithm) {
      predictionsByIndex.set(prediction.stopPathIndex, prediction)
    }
  }
  const features: StopFeature[] = (vehicle.pattern?.stopPaths ?? [])
    .filter((p) => p.stop?.stopLat != null && p.stop?.stopLon != null)
    .map((p) => {
      const name = p.stop!.stopName ?? p.stopId
      const state = stateFor(p.stopPathIndex, nextIndex)
      const prediction = predictionsByIndex.get(p.stopPathIndex)
      const eta = state === 'passed'
        ? null
        : p.stopPathIndex === 0
          ? prediction?.predictedDeparture ?? prediction?.predictedArrival
          : prediction?.predictedArrival
      return {
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [p.stop!.stopLon as number, p.stop!.stopLat as number] as LngLat,
        },
        properties: {
          name,
          label: eta ? `${name}\n${formatClock(eta, { timeZone, locale })}` : name,
          state,
        },
      }
    })
  return { type: 'FeatureCollection' as const, features }
}

function stateFor(index: number, nextIndex: number): StopState {
  if (nextIndex < 0) return 'upcoming'
  if (index < nextIndex) return 'passed'
  if (index === nextIndex) return 'current'
  return 'upcoming'
}

export function boundsOf(coords: LngLat[]): [LngLat, LngLat] | null {
  if (coords.length === 0) return null
  let minLon = Infinity,
    minLat = Infinity,
    maxLon = -Infinity,
    maxLat = -Infinity
  for (const [lon, lat] of coords) {
    minLon = Math.min(minLon, lon)
    minLat = Math.min(minLat, lat)
    maxLon = Math.max(maxLon, lon)
    maxLat = Math.max(maxLat, lat)
  }
  return [
    [minLon, minLat],
    [maxLon, maxLat],
  ]
}

/** Distinct algorithms present in the predictions, order preserved. */
export function algorithmsIn(predictions: Prediction[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const p of predictions) {
    if (p.algorithm && !seen.has(p.algorithm)) {
      seen.add(p.algorithm)
      out.push(p.algorithm)
    }
  }
  return out
}

/** Preserve explicit selection; otherwise honor feed configuration, never global error rankings. */
export function preferredAlgorithm(
  available: string[],
  configured: string[],
  selected?: string,
): string | undefined {
  if (selected && available.includes(selected)) return selected
  return configured.find((algorithm) => available.includes(algorithm)) ?? available[0]
}

/**
 * Whether to ask for the trip's late assessment yet: only once the algorithm the page shows is
 * settled — selected explicitly, or picked from the feed's configured order. Before that the
 * backend would pick one itself (the first with late assessments, e.g. SMILE_GBM in shadow) and its
 * badges could flash for one poll.
 */
export function latenessQueryEnabled(
  feedCode: string | undefined,
  algorithm: string | undefined,
  configured: string[],
  selected?: string,
): boolean {
  return !!feedCode && !!algorithm && (!!selected || configured.length > 0)
}

export function accuracyFor(
  algorithm: string | undefined,
  accuracy: Accuracy[],
): Accuracy | undefined {
  if (!algorithm) return undefined
  return accuracy.find((a) => a.algorithm === algorithm) ?? undefined
}

export interface StopRow {
  stopPathIndex: number
  stopId: string
  stopName: string
  state: StopState
  scheduledArrival: string | null
  /** predicted time if still upcoming, actual time once the stop is reached */
  eta: string | null
  arrived: boolean
  /** seconds vs schedule (predicted or actual − scheduled); null if unknown */
  deltaVsScheduleSec: number | null
  /** for reached stops: actual − predicted, in seconds (prediction error) */
  errorVsPredictionSec: number | null
  confidenceSec: number | null
  actualReportGapSec: number | null
  actualReliable: boolean | null
  /** predicted arrival range around `eta`, in signed seconds; null once reached, at stop 0 or without a range */
  rangeLowSec: number | null
  rangeHighSec: number | null
}

/** One row per pattern stop, joined with the selected algorithm's prediction. */
export function stopRows(
  vehicle: Pick<Vehicle, 'pattern' | 'stopPathIndex'>,
  predictions: Prediction[],
  algorithm: string | undefined,
): StopRow[] {
  const byIndex = new Map<number, Prediction>()
  for (const p of predictions) {
    if (p.algorithm === algorithm) byIndex.set(p.stopPathIndex, p)
  }
  const nextIndex = vehicle.stopPathIndex ?? -1

  return (vehicle.pattern?.stopPaths ?? [])
    .slice()
    .sort((a, b) => a.stopPathIndex - b.stopPathIndex)
    .map((sp) => {
      const p = byIndex.get(sp.stopPathIndex)
      const actual =
        sp.stopPathIndex === 0 ? (p?.actualDeparture ?? p?.actualArrival) : p?.actualArrival
      const predicted =
        sp.stopPathIndex === 0
          ? (p?.predictedDeparture ?? p?.predictedArrival)
          : p?.predictedArrival
      const scheduled =
        sp.stopPathIndex === 0
          ? (p?.scheduledDeparture ?? p?.scheduledArrival)
          : p?.scheduledArrival
      const arrived = !!actual
      const eta = actual ?? predicted ?? null
      // The range is relative to predictedArrival; stop 0 shows a departure instead.
      const ranged = !arrived && sp.stopPathIndex !== 0
      return {
        stopPathIndex: sp.stopPathIndex,
        stopId: sp.stopId,
        stopName: sp.stop?.stopName ?? sp.stopId,
        state: arrived ? 'passed' : stateFor(sp.stopPathIndex, nextIndex),
        scheduledArrival: scheduled ?? null,
        eta,
        arrived,
        deltaVsScheduleSec: diffSec(eta, scheduled ?? null),
        errorVsPredictionSec: arrived ? diffSec(actual ?? null, predicted ?? null) : null,
        confidenceSec: p?.confidenceSec ?? null,
        actualReportGapSec: p?.actualReportGapSec ?? null,
        actualReliable: p?.actualReliable ?? null,
        rangeLowSec: ranged ? (p?.rangeLowSec ?? null) : null,
        rangeHighSec: ranged ? (p?.rangeHighSec ?? null) : null,
      }
    })
}

function diffSec(a: string | null, b: string | null): number | null {
  if (!a || !b) return null
  return Math.round((new Date(a).getTime() - new Date(b).getTime()) / 1000)
}

const COMPASS = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW']

/** Compass label for a bearing in degrees, e.g. 63 → "NE". */
export function cardinal(bearing: number | null | undefined): string | null {
  if (bearing == null || !Number.isFinite(bearing)) return null
  return COMPASS[Math.round((((bearing % 360) + 360) % 360) / 45) % 8]!
}

interface TrailReport {
  ts: string
  position: { lat: number | null; lon: number | null } | null
}

/** AVL fixes as an oldest→newest [lon, lat] polyline. */
export function trailCoords(reports: TrailReport[]): LngLat[] {
  return [...reports]
    .filter((r) => r.position?.lat != null && r.position?.lon != null)
    .sort((a, b) => a.ts.localeCompare(b.ts))
    .map((r) => [r.position!.lon as number, r.position!.lat as number])
}
