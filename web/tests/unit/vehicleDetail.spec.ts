// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { preferredAlgorithm, stopRows } from '../../app/utils/vehicleDetail'

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
      stopPaths: [
        {
          stopPathIndex: 0,
          stopId: 'S1',
          waitStop: true,
          stop: null,
          pathGeometry: null,
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
