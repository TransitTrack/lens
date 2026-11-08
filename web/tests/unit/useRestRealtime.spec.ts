import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useFeedPredictionAccuracyQuery, useVehicleDetailQuery, useVehicleTrailQuery } from '../../app/composables/useRestRealtime'
import { json, page, stubFetch } from '../helpers/stubFetch'

describe('REST realtime queries', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('loads vehicle detail and prediction data from versioned endpoints', async () => {
    const { fetcher, calls } = stubFetch(({ url }) =>
      url.includes('/predictions?') || url.includes('/prediction-accuracy?') ? page([]) : json({ vehicleId: 'bus' }))
    const scope = effectScope()
    const query = scope.run(() => useVehicleDetailQuery(() => ({ variables: { feedCode: 'f', vehicleId: 'bus' } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicle?.vehicleId).toBe('bus'))
    // The vehicle and its predictions only: the feed-wide accuracy is its own (rarely polled) query.
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(calls.some(({ url }) => url.includes('prediction-accuracy'))).toBe(false)
    expect(calls.some(({ url }) => url.includes('include='))).toBe(false)
    scope.stop()
  })

  it('asks for only the selected algorithm\'s predictions', async () => {
    const { calls } = stubFetch(({ url }) => (url.includes('/predictions?') ? page([]) : json({ vehicleId: 'bus' })))
    const scope = effectScope()
    const query = scope.run(() => useVehicleDetailQuery(() => ({ variables: { feedCode: 'f3', vehicleId: 'bus', algorithm: 'KALMAN' } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicle?.vehicleId).toBe('bus'))
    expect(calls.find(({ url }) => url.includes('/predictions?'))!.url).toContain('algorithm=KALMAN')
    scope.stop()
  })

  it('resolves predicted and current stops from the vehicle\'s pattern, without a /stops request', async () => {
    const { calls } = stubFetch(({ url }) => {
      const path = new URL(url).pathname
      if (path.endsWith('/avl-feeds')) return page([{ code: 'f6', gtfsFeedCode: 'g6' }])
      if (path.endsWith('/avl/vehicles/bus')) return json({ vehicleId: 'bus', revisionId: '51', tripId: 'T', currentStopId: 'S1' })
      if (path.endsWith('/trips/T')) return json({ tripId: 'T', routeId: 'R', serviceId: 'WK', patternKey: 'P', blockId: null, shapeId: null })
      if (path.endsWith('/routes/R')) return json({ routeId: 'R' })
      if (path.endsWith('/trip-patterns/P')) return json({ patternKey: 'P', geometry: null })
      if (path.endsWith('/stop-paths')) return page([{ patternKey: 'P', stopId: 'S1', stopPathIndex: 0 }, { patternKey: 'P', stopId: 'S2', stopPathIndex: 1 }])
      if (path.endsWith('/routes/R/stops')) return page([{ stopId: 'S1', stopName: 'One' }, { stopId: 'S2', stopName: 'Two' }])
      if (path.endsWith('/avl/vehicles/bus/predictions')) return page([{ stopPathIndex: 0, stopId: 'S1' }, { stopPathIndex: 1, stopId: 'S2' }])
      return new Response('{}', { status: 404, headers: { 'Content-Type': 'application/json' } })
    })
    const scope = effectScope()
    const query = scope.run(() => useVehicleDetailQuery(() => ({ variables: { feedCode: 'f6', vehicleId: 'bus' } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicle?.currentStop?.stopName).toBe('One'))
    expect(query.result.value!.vehiclePredictions.map((p) => p.stop?.stopName)).toEqual(['One', 'Two'])
    expect(calls.some(({ url }) => /\/feeds\/[^/]+\/stops\?/.test(url))).toBe(false)
    scope.stop()
  })

  it('loads the vehicle trail summary in one request, not the raw reports', async () => {
    const summary = { reportCount: 2, track: [{ lat: 1, lon: 2 }], speeds: [{ distanceAlongTripM: 5, speedMps: 3 }], avgReportIntervalSec: 15 }
    const { fetcher, calls } = stubFetch(() => json(summary))
    const scope = effectScope()
    const query = scope.run(() => useVehicleTrailQuery(() => ({ variables: { feedCode: 'f7', vehicleId: 'bus', limit: 200 } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicleTrail.reportCount).toBe(2))
    expect(query.result.value!.vehicleTrail.speeds).toEqual(summary.speeds)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(calls[0]!.url).toContain('/feeds/f7/avl/vehicles/bus/trail?limit=200')
    scope.stop()
  })

  it('loads the feed prediction accuracy on its own', async () => {
    const { fetcher, calls } = stubFetch(() => page([]))
    const scope = effectScope()
    const query = scope.run(() => useFeedPredictionAccuracyQuery(() => ({ variables: { feedCode: 'f2' } })))!
    await vi.waitFor(() => expect(query.result.value?.predictionAccuracy).toEqual([]))
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(calls[0]!.url).toContain('/feeds/f2/avl/prediction-accuracy?')
    scope.stop()
  })
})
