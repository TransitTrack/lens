import { afterEach, describe, expect, it, vi } from 'vitest'
import { optimizationRunQuery } from '~/composables/api/queries/optimization/optimizationRunQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { id: 9 }

describe('optimizationRunQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /optimization-runs/9 and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = optimizationRunQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/optimization-runs/9')
    expect(options.key).toEqual(['optimization-runs', '9'])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(optimizationRunQuery(params))).toBeNull()
  })
})
