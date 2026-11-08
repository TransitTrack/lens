import { afterEach, describe, expect, it, vi } from 'vitest'
import { predictionAccuracyQuery } from '~/composables/api/queries/avl/predictionAccuracyQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', algorithm: 'KALMAN' as const, sinceDays: 7, routeId: 'r1', directionId: 1, dayType: 'WEEKDAY' as const, timeBand: 'MIDDAY' as const }

describe('predictionAccuracyQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/prediction-accuracy and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = predictionAccuracyQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/prediction-accuracy')
    expect(url.searchParams.get('algorithm')).toBe('KALMAN')
    expect(url.searchParams.get('sinceDays')).toBe('7')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(url.searchParams.get('directionId')).toBe('1')
    expect(url.searchParams.get('dayType')).toBe('WEEKDAY')
    expect(url.searchParams.get('timeBand')).toBe('MIDDAY')
    expect(options.key).toEqual(['avl', 'a', 'prediction-accuracy', { algorithm: 'KALMAN', sinceDays: 7, routeId: 'r1', directionId: 1, dayType: 'WEEKDAY', timeBand: 'MIDDAY' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(predictionAccuracyQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
