import { effectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useRouteDetailQuery } from '../../app/composables/useRestDetails'
import { json, page, stubFetch } from '../helpers/stubFetch'

describe('REST details', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('joins the pattern stop paths and their stops client-side', async () => {
    const { calls } = stubFetch(({ url }) => {
      if (url.includes('/stop-paths')) return page([{ patternKey: 'p', stopPathIndex: 0, stopId: 's1' }])
      if (url.includes('/trip-patterns')) return page([{ patternKey: 'p' }])
      if (url.includes('/stops')) return page([{ stopId: 's1', stopName: 'First' }])
      return json({ routeId: 'r' })
    })
    const scope = effectScope()
    const query = scope.run(() => useRouteDetailQuery(() => ({ variables: { feedCode: 'f', routeId: 'r' } })))!
    await vi.waitFor(() => expect(query.result.value?.route?.routeId).toBe('r'))
    const [pattern] = query.result.value!.tripPatterns
    expect(pattern?.patternKey).toBe('p')
    expect(pattern?.stopPaths[0]?.stop?.stopName).toBe('First')
    expect(calls.some(({ url }) => url.includes('include='))).toBe(false)
    scope.stop()
  })
})
