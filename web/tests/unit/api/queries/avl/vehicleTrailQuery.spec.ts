import { afterEach, describe, expect, it, vi } from 'vitest'
import { vehicleTrailQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', vehicleId: 'v1', limit: 50 }

describe('vehicleTrailQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/vehicles/v1/trail and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = vehicleTrailQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/vehicles/v1/trail')
    expect(url.searchParams.get('limit')).toBe('50')
    expect(options.key).toEqual(['avl', 'a', 'trail', { vehicleId: 'v1', limit: 50 }])
  })
})
