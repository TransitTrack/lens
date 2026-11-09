import { afterEach, describe, expect, it, vi } from 'vitest'
import { stopPredictionsQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', stopId: 's1', routeId: 'r1', directionId: 0, algorithm: 'KALMAN' as const }

describe('stopPredictionsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/stops/s1/predictions and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = stopPredictionsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/stops/s1/predictions')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(url.searchParams.get('directionId')).toBe('0')
    expect(url.searchParams.get('algorithm')).toBe('KALMAN')
    expect(options.key).toEqual(['avl', 'a', 'stop-predictions', { stopId: 's1', routeId: 'r1', directionId: 0, algorithm: 'KALMAN' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(stopPredictionsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
