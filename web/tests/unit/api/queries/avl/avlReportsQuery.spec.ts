import { afterEach, describe, expect, it, vi } from 'vitest'
import { avlReportsQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { avlFeedCode: 'a', vehicleId: 'v1', limit: 5, since: '2026-01-01T00:00:00Z' }

describe('avlReportsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/vehicles/v1/reports and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = avlReportsQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/vehicles/v1/reports')
    expect(url.searchParams.get('limit')).toBe('5')
    expect(url.searchParams.get('since')).toBe('2026-01-01T00:00:00Z')
    expect(options.key).toEqual(['avl', 'a', 'reports', { vehicleId: 'v1', limit: 5, since: '2026-01-01T00:00:00Z' }])
  })
})
