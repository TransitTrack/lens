// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { latenessQueryEnabled, preferredAlgorithm, stopRows } from '../../app/utils/vehicleDetail'

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

describe('latenessQueryEnabled', () => {
  it('waits for the feed and an algorithm', () => {
    expect(latenessQueryEnabled(undefined, 'KALMAN', ['KALMAN'])).toBe(false)
    expect(latenessQueryEnabled('stpt', undefined, ['KALMAN'])).toBe(false)
  })

  it('waits for the feed configuration unless the algorithm was selected', () => {
    // Predictions arrived before the feed: the fallback pick is not the page's settled choice.
    expect(latenessQueryEnabled('stpt', 'SMILE_GBM', [])).toBe(false)
    expect(latenessQueryEnabled('stpt', 'SMILE_GBM', [], 'SMILE_GBM')).toBe(true)
    expect(latenessQueryEnabled('stpt', 'KALMAN', ['KALMAN', 'SMILE_GBM'])).toBe(true)
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
    rangeLowSec: null,
    rangeHighSec: null,
    lateProbability: null,
    late: null,
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

  describe('prediction range', () => {
    const twoStops: Parameters<typeof stopRows>[0] = {
      stopPathIndex: 1,
      pattern: {
        patternKey: 'pattern',
        lengthM: 0,
        stopPaths: [
          { stopPathIndex: 0, stopId: 'S1', waitStop: true, stop: null, pathGeometry: null },
          { stopPathIndex: 1, stopId: 'S2', waitStop: false, stop: null, pathGeometry: null },
        ],
      },
    }
    const ranged = {
      ...prediction,
      stopPathIndex: 1,
      actualDeparture: null,
      algorithm: 'SMILE_GBM' as const,
      rangeLowSec: -60,
      rangeHighSec: 120,
    }

    it('carries the range of an upcoming arrival', () => {
      expect(stopRows(twoStops, [ranged], 'SMILE_GBM')[1]).toMatchObject({
        eta: ranged.predictedArrival,
        rangeLowSec: -60,
        rangeHighSec: 120,
      })
    })

    it('drops the range once the stop is reached', () => {
      const reached = { ...ranged, actualArrival: '2026-09-07T08:01:00Z' }
      expect(stopRows(twoStops, [reached], 'SMILE_GBM')[1]).toMatchObject({
        arrived: true,
        rangeLowSec: null,
        rangeHighSec: null,
      })
    })

    it('has no range at the first stop, which shows a departure', () => {
      const first = { ...ranged, stopPathIndex: 0 }
      expect(stopRows(twoStops, [first], 'SMILE_GBM')[0]).toMatchObject({
        rangeLowSec: null,
        rangeHighSec: null,
      })
    })
  })
})
