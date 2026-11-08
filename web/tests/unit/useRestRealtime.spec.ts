import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useVehicleDetailQuery } from '../../app/composables/useRestRealtime'
import { json, page, stubFetch } from '../helpers/stubFetch'

describe('REST realtime queries', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('loads vehicle detail and prediction data from versioned endpoints', async () => {
    const { fetcher, calls } = stubFetch(({ url }) =>
      url.includes('/predictions?') || url.includes('/prediction-accuracy?') ? page([]) : json({ vehicleId: 'bus' }))
    const scope = effectScope()
    const query = scope.run(() => useVehicleDetailQuery(() => ({ variables: { feedCode: 'f', vehicleId: 'bus' } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicle?.vehicleId).toBe('bus'))
    expect(fetcher).toHaveBeenCalledTimes(3)
    expect(calls.some(({ url }) => url.includes('include='))).toBe(false)
    scope.stop()
  })
})
