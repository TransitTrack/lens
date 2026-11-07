import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useRouteDetailQuery } from '../../app/composables/useRestDetails'

describe('REST details', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('joins the pattern stop paths and their stops client-side', async () => {
    const page = (rows: unknown[]) => new Response(JSON.stringify(rows), { headers: { 'X-Total-Count': String(rows.length) } })
    const fetcher = vi.fn(async (input: string) => {
      if (input.includes('/stop-paths')) return page([{ patternKey: 'p', stopPathIndex: 0, stopId: 's1' }])
      if (input.includes('/trip-patterns')) return page([{ patternKey: 'p' }])
      if (input.includes('/stops')) return page([{ stopId: 's1', stopName: 'First' }])
      return new Response(JSON.stringify({ routeId: 'r' }))
    })
    vi.stubGlobal('fetch', fetcher)
    const scope = effectScope()
    const query = scope.run(() => useRouteDetailQuery(() => ({ variables: { feedCode: 'f', routeId: 'r' } })))!
    await vi.waitFor(() => expect(query.result.value?.route?.routeId).toBe('r'))
    const [pattern] = query.result.value!.tripPatterns
    expect(pattern?.patternKey).toBe('p')
    expect(pattern?.stopPaths[0]?.stop?.stopName).toBe('First')
    expect(fetcher.mock.calls.some(([url]) => url.includes('include='))).toBe(false)
    scope.stop()
  })
})
