import { afterEach, describe, expect, it, vi } from 'vitest'
import { allRoutesAdherenceQuery } from '~/composables/api/queries/avl/allRoutesAdherenceQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'a', startDate: '2026-01-01', endDate: '2026-01-31' }

describe('allRoutesAdherenceQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/reports/route-adherence and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = allRoutesAdherenceQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/reports/route-adherence')
    expect(url.searchParams.get('startDate')).toBe('2026-01-01')
    expect(url.searchParams.get('endDate')).toBe('2026-01-31')
    expect(options.key).toEqual(['avl', 'a', 'all-routes-adherence', { startDate: '2026-01-01', endDate: '2026-01-31' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(allRoutesAdherenceQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
