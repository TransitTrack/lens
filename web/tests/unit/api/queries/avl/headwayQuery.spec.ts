import { afterEach, describe, expect, it, vi } from 'vitest'
import { headwayQuery } from '~/composables/api/queries/avl/headwayQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', stopId: 's1', routeId: 'r1', directionId: 0, algorithm: 'KALMAN' as const }

describe('headwayQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/stops/s1/headway and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = headwayQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/stops/s1/headway')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(url.searchParams.get('directionId')).toBe('0')
    expect(url.searchParams.get('algorithm')).toBe('KALMAN')
    expect(options.key).toEqual(['avl', 'a', 'headway', { stopId: 's1', routeId: 'r1', directionId: 0, algorithm: 'KALMAN' }])
  })
})
