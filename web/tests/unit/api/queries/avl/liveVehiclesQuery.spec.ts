import { afterEach, describe, expect, it, vi } from 'vitest'
import { liveVehiclesQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('liveVehiclesQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads every vehicle from the feed map endpoint', async () => {
    const { calls } = stubFetch(() => json([{ vehicleId: '1' }, { vehicleId: '2' }]))
    const options = liveVehiclesQuery({ feedCode: 'feed / one' })
    const result = await runQuery(options)
    expect(result.map((v) => v.vehicleId)).toEqual(['1', '2'])
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/feeds/feed%20%2F%20one/vehicles')
    expect(options.key).toEqual(['avl', 'feed / one', 'live-vehicles', {}])
  })

  it('throws on a failed response', async () => {
    stubFetch(() => new Response('boom', { status: 500 }))
    await expect(runQuery(liveVehiclesQuery({ feedCode: 'feed' }))).rejects.toThrow('Vehicle request failed: 500')
  })

  it('throws on a non-array response', async () => {
    stubFetch(() => json({}))
    await expect(runQuery(liveVehiclesQuery({ feedCode: 'feed' }))).rejects.toThrow('Invalid vehicle response')
  })
})
