import { afterEach, describe, expect, it, vi } from 'vitest'
import { vehiclePredictionsQuery } from '~/composables/api/queries/avl/vehiclePredictionsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', vehicleId: 'v1', algorithm: 'KALMAN' as const }

describe('vehiclePredictionsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/vehicles/v1/predictions and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = vehiclePredictionsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/vehicles/v1/predictions')
    expect(url.searchParams.get('algorithm')).toBe('KALMAN')
    expect(options.key).toEqual(['avl', 'a', 'vehicle-predictions', { vehicleId: 'v1', algorithm: 'KALMAN' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(vehiclePredictionsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
