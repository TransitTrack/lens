// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import {
  adherenceProfile,
  preferredAlgorithm,
  segmentSpeeds,
  speedProfile,
  standstills,
  stopRows,
  stripLabelIndexes,
  tripStrip,
  worstLoss,
  type StripStop,
  type StopRow,
  type TripStrip,
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
    distanceM: offset * 1000,
    adherenceSec: null,
    lossSec: null,
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

describe('delay curve', () => {
  const paths = [0, 1000, 1000, 1000].map((lengthM, stopPathIndex) => ({
    stopPathIndex,
    lengthM,
    scheduleAdherenceStop: false,
  }))
  const reached = (i: number, deltaVsScheduleSec: number | null): StopRow => ({
    stopPathIndex: i,
    stopId: `s${i}`,
    stopName: `Stop ${i}`,
    state: 'passed',
    scheduledArrival: null,
    eta: '2026-11-06T21:00:00Z',
    arrived: true,
    deltaVsScheduleSec,
    errorVsPredictionSec: null,
    confidenceSec: null,
    actualReportGapSec: null,
    actualReliable: null,
  })
  const ahead = (i: number): StopRow => ({ ...reached(i, null), state: 'upcoming', arrived: false })
  const rows = [reached(0, 20), reached(1, 30), reached(2, 125), ahead(3)]
  const strip = tripStrip({ distanceAlongTripM: 2500, pattern: { stopPaths: paths } }, rows)!

  it('records the delay gained since the previous reached stop', () => {
    expect(strip.stops.map((s) => [s.adherenceSec, s.lossSec])).toEqual([
      [20, null],
      [30, 10],
      [125, 95],
      [null, null],
    ])
  })

  it('runs from the first reached stop to the vehicle now', () => {
    const points = adherenceProfile(strip, 140)
    expect(points.map((p) => [Number(p.offset.toFixed(3)), p.sec])).toEqual([
      [0, 20],
      [0.333, 30],
      [0.667, 125],
      [0.833, 140],
    ])
  })

  it('picks the stretch that gained the most delay, ignoring small changes', () => {
    const worst = worstLoss(adherenceProfile(strip, 140))!
    expect(worst.lossSec).toBe(95)
    expect(Number(worst.from.offset.toFixed(3))).toBe(0.333)
    expect(worstLoss(adherenceProfile(strip, 140), 120)).toBeNull()
  })
})

describe('speed profile', () => {
  const stop = (i: number, distanceM: number): StripStop => ({
    stopPathIndex: i,
    name: `Stop ${i}`,
    offset: distanceM / 2000,
    timepoint: false,
    passed: true,
    next: false,
    eta: null,
    arrived: true,
    distanceM,
    adherenceSec: null,
    lossSec: null,
  })
  const strip: TripStrip = {
    stops: [stop(0, 0), stop(1, 1000), stop(2, 2000)],
    vehicleOffset: 0.9,
    startM: 0,
    spanM: 2000,
  }
  const report = (ts: string, distanceAlongTripM: number, speedMps: number | null, tripId = 'T1') => ({
    ts,
    speedMps,
    match: { distanceAlongTripM, trip: { tripId } },
  })
  const reports = [
    report('2026-11-06T21:03:00Z', 1500, 0),
    report('2026-11-06T21:01:00Z', 400, 10),
    report('2026-11-06T21:02:00Z', 990, 0),
    report('2026-11-06T21:00:00Z', 300, 12, 'OTHER'),
    report('2026-11-06T21:04:00Z', 1800, null),
  ]

  it('keeps only the current trip and orders reports by distance', () => {
    const points = speedProfile(strip, reports, 'T1')
    expect(points.map((p) => [p.distanceM, p.speedMps])).toEqual([
      [400, 10],
      [990, 0],
      [1500, 0],
    ])
    expect(speedProfile(strip, reports, null)).toEqual([])
  })

  it('averages speed on the stretch leading to each stop', () => {
    const speeds = segmentSpeeds(strip, speedProfile(strip, reports, 'T1'))
    expect(speeds.get(1)).toBe(5)
    expect(speeds.get(2)).toBe(0)
    expect(speeds.has(0)).toBe(false)
  })

  it('flags standing still away from stops, not dwelling at one', () => {
    const still = standstills(strip, speedProfile(strip, reports, 'T1'))
    expect(still.map((p) => p.distanceM)).toEqual([1500])
  })
})
