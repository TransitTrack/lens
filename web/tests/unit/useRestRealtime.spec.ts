import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useVehicleDetailQuery } from '../../app/composables/useRestRealtime'

describe('REST realtime queries', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('loads vehicle detail and prediction data from versioned endpoints', async () => {
    const fetcher = vi.fn(async (url: string) => {
      const list = url.includes('/predictions?') || url.includes('/prediction-accuracy?')
      const body = list ? [] : { vehicleId: 'bus' }
      return new Response(JSON.stringify(body), { headers: list ? { 'X-Total-Count': '0' } : {} })
    })
    vi.stubGlobal('fetch', fetcher)
    const scope = effectScope()
    const query = scope.run(() => useVehicleDetailQuery(() => ({ variables: { feedCode: 'f', vehicleId: 'bus' } })))!
    await vi.waitFor(() => expect(query.result.value?.vehicle?.vehicleId).toBe('bus'))
    expect(fetcher).toHaveBeenCalledTimes(3)
    expect(fetcher.mock.calls.some(([url]) => url.includes('include='))).toBe(false)
    scope.stop()
  })
})
