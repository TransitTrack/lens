import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useExploreRoutesQuery } from '../../app/composables/useRestGtfs'

describe('REST GTFS queries', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('loads routes and agencies from their REST endpoints', async () => {
    const fetcher = vi.fn(async (input: string) => {
      const rows = input.includes('/routes?') ? [{ routeId: 'r' }] : [{ agencyId: 'a', agencyName: 'Transit' }]
      return new Response(JSON.stringify(rows), { headers: { 'X-Total-Count': '1' } })
    })
    vi.stubGlobal('fetch', fetcher)
    const scope = effectScope()
    const query = scope.run(() => useExploreRoutesQuery(() => ({ variables: { feedCode: 'f' } })))!
    await vi.waitFor(() => expect(query.result.value?.routes[0]?.routeId).toBe('r'))
    expect(query.result.value?.agencies[0]?.agencyName).toBe('Transit')
    expect(fetcher).toHaveBeenCalledTimes(2)
    scope.stop()
  })
})
