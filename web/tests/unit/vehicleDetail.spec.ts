// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import {
  preferredAlgorithm,
  stopRows,
  stripLabelIndexes,
  tripStrip,
  type StripStop,
  type StopRow,
} from '../../app/utils/vehicleDetail'

describe('preferredAlgorithm', () => {
  it('uses the feed-designated algorithm rather than prediction ordering', () => {
    expect(
      preferredAlgorithm(['SCHEDULE_ADHERENCE', 'KALMAN'], ['KALMAN', 'SCHEDULE_ADHERENCE']),
    ).toBe('KALMAN')
  })

  it('falls back only to an available algorithm', () => {
    expect(preferredAlgorithm(['KALMAN'], ['SCHEDULE_ADHERENCE'])).toBe('KALMAN')
    expect(preferredAlgorithm([], ['KALMAN'])).toBeUndefined()
  })

  it('updates the default when feed configuration arrives after predictions', () => {
    const available = ['SCHEDULE_ADHERENCE', 'KALMAN']
    expect(preferredAlgorithm(available, [])).toBe('SCHEDULE_ADHERENCE')
    expect(preferredAlgorithm(available, ['KALMAN'])).toBe('KALMAN')
  })

  it('preserves an explicit available selection, but not a disappeared algorithm', () => {
    expect(
      preferredAlgorithm(['KALMAN', 'SCHEDULE_ADHERENCE'], ['KALMAN'], 'SCHEDULE_ADHERENCE'),
    ).toBe('SCHEDULE_ADHERENCE')
    expect(preferredAlgorithm(['KALMAN'], ['KALMAN'], 'SCHEDULE_ADHERENCE')).toBe('KALMAN')
  })
})

describe('stopRows', () => {
  const vehicle: Parameters<typeof stopRows>[0] = {
    stopPathIndex: 0,
    pattern: {
      patternKey: 'pattern',
      lengthM: 0,
      geometry: null,
      stopPaths: [
        {
          stopPathIndex: 0,
          stopId: 'S1',
          waitStop: true,
          stop: null,
        },
      ],
    },
  }
  const prediction: Parameters<typeof stopRows>[1][number] = {
    stopPathIndex: 0,
    stop: null,
    scheduledArrival: '2026-09-07T08:00:00Z',
    scheduledDeparture: '2026-09-07T08:05:00Z',
    predictedArrival: '2026-09-07T08:00:00Z',
    predictedDeparture: '2026-09-07T08:05:00Z',
    actualArrival: null,
    actualDeparture: '2026-09-07T08:06:00Z',
    actualReportGapSec: 30,
    actualReliable: true,
    sourceReportTs: '2026-09-07T08:04:00Z',
    algorithm: 'KALMAN',
    confidenceSec: 20,
  }

  it('marks the first stop passed using its actual departure', () => {
    expect(stopRows(vehicle, [prediction], 'KALMAN')[0]).toMatchObject({
      arrived: true,
      state: 'passed',
      eta: prediction.actualDeparture,
      scheduledArrival: prediction.scheduledDeparture,
      deltaVsScheduleSec: 60,
      errorVsPredictionSec: 60,
    })
  })

  it('uses predicted departure while waiting at the first stop', () => {
    const waiting = { ...prediction, actualDeparture: null }
    expect(stopRows(vehicle, [waiting], 'KALMAN')[0]).toMatchObject({
      arrived: false,
      eta: prediction.predictedDeparture,
      deltaVsScheduleSec: 0,
      errorVsPredictionSec: null,
    })
  })

  it('preserves unreliable crossing metadata for display', () => {
    const sparse = { ...prediction, actualReportGapSec: 180, actualReliable: false }
    expect(stopRows(vehicle, [sparse], 'KALMAN')[0]).toMatchObject({
      actualReportGapSec: 180,
      actualReliable: false,
    })
  })
})

describe('tripStrip', () => {
  const paths = [
    // lengthM is each stop's segment from the previous stop, listed out of order on purpose
    { stopPathIndex: 2, lengthM: 3000, scheduleAdherenceStop: true },
    { stopPathIndex: 0, lengthM: 0, scheduleAdherenceStop: true },
    { stopPathIndex: 1, lengthM: 1000, scheduleAdherenceStop: false },
  ]
  const row = (i: number, state: StopRow['state']): StopRow => ({
    stopPathIndex: i,
    stopId: `s${i}`,
    stopName: `Stop ${i}`,
    state,
    scheduledArrival: null,
    eta: null,
    arrived: state === 'passed',
    deltaVsScheduleSec: null,
    errorVsPredictionSec: null,
    confidenceSec: null,
    actualReportGapSec: null,
    actualReliable: null,
  })
  const rows = [row(0, 'passed'), row(1, 'current'), row(2, 'upcoming')]

  it('places stops and the vehicle at their real distance along the trip', () => {
    const strip = tripStrip({ distanceAlongTripM: 500, pattern: { stopPaths: paths } }, rows)!
    expect(strip.stops.map((s) => s.offset)).toEqual([0, 0.25, 1])
    expect(strip.vehicleOffset).toBe(0.125)
    expect(strip.stops.map((s) => [s.passed, s.next, s.timepoint])).toEqual([
      [true, false, true],
      [false, true, false],
      [false, false, true],
    ])
  })

  it('clamps the vehicle to the trip and has no position without a distance', () => {
    const pattern = { stopPaths: paths }
    expect(tripStrip({ distanceAlongTripM: 9000, pattern }, rows)!.vehicleOffset).toBe(1)
    expect(tripStrip({ distanceAlongTripM: null, pattern }, rows)!.vehicleOffset).toBeNull()
  })

  it('needs at least two stops spread over a distance', () => {
    expect(tripStrip({ distanceAlongTripM: 0, pattern: { stopPaths: paths.slice(1, 2) } }, rows)).toBeNull()
    expect(tripStrip({ distanceAlongTripM: 0, pattern: null }, rows)).toBeNull()
  })
})

describe('stripLabelIndexes', () => {
  const stop = (i: number, offset: number, extra: Partial<StripStop> = {}): StripStop => ({
    stopPathIndex: i,
    name: `Stop ${i}`,
    offset,
    timepoint: false,
    passed: false,
    next: false,
    eta: null,
    arrived: false,
    ...extra,
  })

  it('keeps the next stop and both ends, dropping labels that would overlap', () => {
    const stops = [stop(0, 0), stop(1, 0.05), stop(2, 0.5, { next: true }), stop(3, 0.55), stop(4, 1)]
    expect([...stripLabelIndexes(stops, 1000, 100)].sort()).toEqual([0, 2, 4])
  })

  it('prefers timepoints over ordinary stops for the remaining room', () => {
    const stops = [stop(0, 0), stop(1, 0.3), stop(2, 0.35, { timepoint: true }), stop(3, 1)]
    expect([...stripLabelIndexes(stops, 1000, 100)].sort()).toEqual([0, 2, 3])
  })

  it('accounts for end labels extending inwards from the strip edges', () => {
    // Stop 1 sits 70px in: centred, it would start at 20px, inside the first label (0..100px).
    const stops = [stop(0, 0), stop(1, 0.07), stop(2, 1)]
    expect([...stripLabelIndexes(stops, 1000, 100)].sort()).toEqual([0, 2])
  })
})
