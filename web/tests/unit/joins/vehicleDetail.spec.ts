import { describe, expect, it } from 'vitest'
import { gzipSync, strToU8 } from 'fflate'
import { ref } from 'vue'
import type { Block, BlockTrip, Route, Shape, Stop, StopPath, StopPrediction, Trip, TripPattern, Vehicle } from '~/api'
import {
  chainError, chainLoading, chainPending, missingStopIds, needsShape, readPending, vehicleDetail, vehiclePattern,
} from '~/utils/joins/vehicleDetail'

const as = <T>(v: unknown) => v as T
const encode = (value: unknown) => btoa(String.fromCharCode(...gzipSync(strToU8(JSON.stringify(value)))))

const vehicle = as<Vehicle>({ vehicleId: 'bus', revisionId: '51', tripId: 'T', currentStopId: 'S1' })
const trip = as<Trip>({ tripId: 'T', routeId: 'R', serviceId: 'WK', patternKey: 'P', blockId: 'B', shapeId: 'SH' })
const route = as<Route>({ routeId: 'R', routeShortName: '7' })
const pattern = as<TripPattern>({ patternKey: 'P', geometry: null })
const stopPaths = as<StopPath[]>([
  { patternKey: 'P', stopId: 'S1', stopPathIndex: 0 },
  { patternKey: 'P', stopId: 'S2', stopPathIndex: 1 },
  { patternKey: 'Q', stopId: 'S3', stopPathIndex: 0 },
])
const routeStops = as<Stop[]>([{ stopId: 'S1', stopName: 'One' }])
const predictions = as<StopPrediction[]>([{ stopPathIndex: 0, stopId: 'S1' }, { stopPathIndex: 1, stopId: 'S2' }])

describe('vehiclePattern', () => {
  it('carries only the trip pattern\'s stop paths, each with its route stop', () => {
    const joined = vehiclePattern(trip, pattern, stopPaths, routeStops)
    expect(joined!.stopPaths.map((p) => [p.stopId, p.stop?.stopName ?? null])).toEqual([['S1', 'One'], ['S2', null]])
  })

  it('is null without a pattern', () => {
    expect(vehiclePattern(trip, null, stopPaths, routeStops)).toBeNull()
  })
})

describe('missingStopIds', () => {
  it('lists the current and predicted stops not found on the pattern, once each', () => {
    const joined = vehiclePattern(trip, pattern, stopPaths, routeStops)
    const extra = as<StopPrediction[]>([...predictions, { stopPathIndex: 2, stopId: 'S2' }])
    expect(missingStopIds(as<Vehicle>({ ...vehicle, currentStopId: 'S9' }), extra, joined)).toEqual(['S9', 'S2'])
  })

  it('looks nothing up for an unmatched vehicle', () => {
    expect(missingStopIds(as<Vehicle>({ vehicleId: 'bus', currentStopId: 'S9' }), predictions, null)).toEqual([])
  })
})

describe('needsShape', () => {
  it('asks for the shape only when the pattern has no usable line', () => {
    expect(needsShape(null)).toBe(true)
    expect(needsShape(as<TripPattern>({ geometry: encode({ type: 'LineString', coordinates: [[1, 2]] }) }))).toBe(true)
    expect(needsShape(as<TripPattern>({ geometry: encode({ type: 'LineString', coordinates: [[1, 2], [3, 4]] }) }))).toBe(false)
  })
})

describe('vehicleDetail', () => {
  const block = as<Block>({ blockId: 'B', serviceId: 'WK' })
  const blockTrips = as<BlockTrip[]>([{ listIndex: 0, tripId: 'T0' }, { listIndex: 1, tripId: 'T' }])
  const shape = as<Shape>({ shapeId: 'SH', geometry: 'g' })

  it('joins trip, route, shape, pattern, block outline and every stop', () => {
    const { vehicle: v, vehiclePredictions } = vehicleDetail({
      vehicle, predictions, trip, route, shape, pattern, stopPaths, routeStops, block, blockTrips,
      stops: as<Stop[]>([{ stopId: 'S2', stopName: 'Two' }]),
    })
    expect(v!.trip).toMatchObject({ tripId: 'T', route: { routeShortName: '7' }, shape: { geometry: 'g' } })
    expect(v!.pattern!.stopPaths.map((p) => p.stop?.stopName ?? null)).toEqual(['One', null])
    expect(v!.block).toEqual({ blockId: 'B', serviceId: 'WK', blockTrips: [
      { listIndex: 0, tripId: 'T0', trip: { tripId: 'T0' } },
      { listIndex: 1, tripId: 'T', trip: { tripId: 'T' } },
    ] })
    expect(v!.currentStop).toMatchObject({ stopName: 'One' })
    expect(vehiclePredictions.map((p) => p.stop?.stopName)).toEqual(['One', 'Two'])
  })

  it('leaves the relations empty when they are not read', () => {
    const { vehicle: v } = vehicleDetail({ vehicle: as<Vehicle>({ ...vehicle, currentStopId: null }), predictions: [], trip })
    expect(v).toMatchObject({ trip: { tripId: 'T', route: null, shape: null }, pattern: null, block: null, currentStop: null })
  })

  it('drops trip, pattern and block for an unmatched vehicle', () => {
    const { vehicle: v, vehiclePredictions } = vehicleDetail({
      vehicle: as<Vehicle>({ vehicleId: 'bus', currentStopId: 'S1' }), predictions, trip, pattern, block,
    })
    expect(v).toMatchObject({ trip: null, pattern: null, block: null, currentStop: null })
    expect(vehiclePredictions.map((p) => p.stop)).toEqual([null, null])
  })

  it('is a null vehicle with stop-less predictions when the vehicle is unknown', () => {
    const detail = vehicleDetail({ vehicle: null, predictions })
    expect(detail.vehicle).toBeNull()
    expect(detail.vehiclePredictions.map((p) => p.stop)).toEqual([null, null])
  })
})

describe('read chain state', () => {
  const read = (enabled: boolean, s: { data?: unknown, isPlaceholderData?: boolean, error?: Error | null, isLoading?: boolean }) => ({
    enabled,
    q: {
      data: ref(s.data), isPlaceholderData: ref(s.isPlaceholderData ?? false),
      error: ref(s.error ?? null), isLoading: ref(s.isLoading ?? false),
    },
  })

  it('is pending while an enabled read has no data of its own key and no error', () => {
    expect(readPending(read(true, {}))).toBe(true)
    expect(readPending(read(true, { data: 'old', isPlaceholderData: true }))).toBe(true)
    expect(readPending(read(true, { data: null }))).toBe(false)
    expect(readPending(read(true, { error: new Error('x') }))).toBe(false)
    expect(readPending(read(false, {}))).toBe(false)
  })

  it('reports any pending, any loading and the first error of the enabled reads', () => {
    const failed = new Error('boom')
    const reads = [read(true, { data: 1, isLoading: false }), read(false, { isLoading: true, error: new Error('off') }), read(true, { error: failed })]
    expect(chainPending(reads)).toBe(false)
    expect(chainLoading(reads)).toBe(false)
    expect(chainError(reads)).toBe(failed)
    const busy = [read(true, { data: 1, isLoading: true }), read(true, {})]
    expect(chainPending(busy)).toBe(true)
    expect(chainLoading(busy)).toBe(true)
    expect(chainError(busy)).toBeNull()
  })
})
