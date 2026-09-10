import { describe, expect, it } from 'vitest'
import {
  haversineM,
  avgIntervalSec,
  blockTripProgress,
  currentStatusLabel,
} from '../../app/utils/vehicleTelemetry'

describe('haversineM', () => {
  it('~0 for identical points', () => {
    expect(haversineM({ lat: 45.75, lon: 21.22 }, { lat: 45.75, lon: 21.22 })).toBeCloseTo(0, 5)
  })

  it('~111 m for 0.001° of latitude', () => {
    const d = haversineM({ lat: 45.75, lon: 21.22 }, { lat: 45.751, lon: 21.22 })
    expect(d).toBeGreaterThan(105)
    expect(d).toBeLessThan(120)
  })

  it('null when a coordinate is missing', () => {
    expect(haversineM({ lat: 1, lon: null }, { lat: 2, lon: 3 })).toBeNull()
    expect(haversineM(null, { lat: 2, lon: 3 })).toBeNull()
  })
})

describe('avgIntervalSec', () => {
  it('mean gap across reports, order-independent', () => {
    const reports = [
      { ts: '2026-09-10T10:00:00Z' },
      { ts: '2026-09-10T10:00:30Z' },
      { ts: '2026-09-10T10:01:30Z' },
    ]
    expect(avgIntervalSec(reports)).toBe(45)
    expect(avgIntervalSec([...reports].reverse())).toBe(45)
  })

  it('null with fewer than two valid timestamps', () => {
    expect(avgIntervalSec([{ ts: '2026-09-10T10:00:00Z' }])).toBeNull()
    expect(avgIntervalSec([])).toBeNull()
  })
})

describe('blockTripProgress', () => {
  const block = [
    { listIndex: 2, trip: { tripId: 'c' } },
    { listIndex: 0, trip: { tripId: 'a' } },
    { listIndex: 1, trip: { tripId: 'b' } },
  ]

  it('1-based position after sorting by listIndex', () => {
    expect(blockTripProgress(block, 'b')).toEqual({ index: 2, total: 3 })
    expect(blockTripProgress(block, 'a')).toEqual({ index: 1, total: 3 })
  })

  it('null when the trip is absent or inputs are empty', () => {
    expect(blockTripProgress(block, 'z')).toBeNull()
    expect(blockTripProgress([], 'a')).toBeNull()
    expect(blockTripProgress(block, null)).toBeNull()
  })
})

describe('currentStatusLabel', () => {
  it('maps known statuses', () => {
    expect(currentStatusLabel('STOPPED_AT')).toBe('Stopped at stop')
    expect(currentStatusLabel('IN_TRANSIT_TO')).toBe('In transit')
  })
  it('passes through unknown, dashes on empty', () => {
    expect(currentStatusLabel('WEIRD')).toBe('WEIRD')
    expect(currentStatusLabel(null)).toBeNull()
  })
})
