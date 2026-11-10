import { afterEach, describe, expect, it, vi } from 'vitest'
import { vehicleQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', vehicleId: 'v1' }

describe('vehicleQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/vehicles/v1 and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = vehicleQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/vehicles/v1')
    expect(options.key).toEqual(['avl', 'a', 'vehicles', { vehicleId: 'v1' }])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(vehicleQuery(params))).toBeNull()
  })
})
