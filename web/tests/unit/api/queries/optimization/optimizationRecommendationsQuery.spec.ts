import { afterEach, describe, expect, it, vi } from 'vitest'
import { optimizationRecommendationsQuery } from '~/composables/api/queries/optimization/optimizationRecommendationsQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { runId: 9, status: 'PENDING' as const, offset: 0, limit: 50 }

describe('optimizationRecommendationsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /optimization-runs/9/recommendations and returns the result', async () => {
    const { calls } = stubFetch(() => json([{ id: 'x' }]))
    const options = optimizationRecommendationsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/optimization-runs/9/recommendations')
    expect(url.searchParams.get('status')).toBe('PENDING')
    expect(url.searchParams.get('offset')).toBe('0')
    expect(url.searchParams.get('limit')).toBe('50')
    expect(options.key).toEqual(['optimization-runs', '9', 'recommendations', { status: 'PENDING', offset: 0, limit: 50 }])
  })
})
