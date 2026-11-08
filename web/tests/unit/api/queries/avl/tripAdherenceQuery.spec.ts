import { afterEach, describe, expect, it, vi } from 'vitest'
import { tripAdherenceQuery } from '~/composables/api/queries/avl/tripAdherenceQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'a', tripId: 't1', startDate: '2026-01-01', endDate: '2026-01-31' }

describe('tripAdherenceQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/trips/t1/adherence and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = tripAdherenceQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/trips/t1/adherence')
    expect(url.searchParams.get('startDate')).toBe('2026-01-01')
    expect(url.searchParams.get('endDate')).toBe('2026-01-31')
    expect(options.key).toEqual(['avl', 'a', 'trip-adherence', { tripId: 't1', startDate: '2026-01-01', endDate: '2026-01-31' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(tripAdherenceQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
