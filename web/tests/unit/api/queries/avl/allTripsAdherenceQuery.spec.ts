import { afterEach, describe, expect, it, vi } from 'vitest'
import { allTripsAdherenceQuery } from '~/composables/api/queries/avl'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'a', routeId: 'r1', startDate: '2026-01-01', endDate: '2026-01-31' }

describe('allTripsAdherenceQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/a/avl/reports/trip-adherence and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = allTripsAdherenceQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/a/avl/reports/trip-adherence')
    expect(url.searchParams.get('startDate')).toBe('2026-01-01')
    expect(url.searchParams.get('endDate')).toBe('2026-01-31')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(options.key).toEqual(['avl', 'a', 'all-trips-adherence', { routeId: 'r1', startDate: '2026-01-01', endDate: '2026-01-31' }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(allTripsAdherenceQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
