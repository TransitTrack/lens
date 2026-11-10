import { afterEach, describe, expect, it, vi } from 'vitest'
import { optimizationRunsQuery } from '~/composables/api/queries/optimization'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', limit: 100, offset: 0 }

describe('optimizationRunsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/f/optimization-runs and returns the result', async () => {
    const { calls } = stubFetch(() => json([{ id: 'x' }]))
    const options = optimizationRunsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/optimization-runs')
    expect(url.searchParams.get('limit')).toBe('100')
    expect(url.searchParams.get('offset')).toBe('0')
    expect(options.key).toEqual(['feeds', 'f', 'optimization-runs', { limit: 100, offset: 0 }])
  })
})
