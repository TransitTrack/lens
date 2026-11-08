import { afterEach, describe, expect, it, vi } from 'vitest'
import { routesByIdsQuery } from '~/composables/api/queries/gtfs/routesByIdsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('routesByIdsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends the sorted, de-duplicated ids and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ routeId: 'a' }]))
    const options = routesByIdsQuery({ feedCode: 'f', revisionId: 7, routeIds: ['b', 'a', 'b'] })
    expect(await runQuery(options)).toEqual([{ routeId: 'a' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/routes')
    expect(url.searchParams.getAll('routeId')).toEqual(['a', 'b'])
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'routes', { routeIds: ['a', 'b'] }])
  })

  it('gives the same key for the same ids in any order', () => {
    expect(routesByIdsQuery({ feedCode: 'f', routeIds: ['b', 'a'] }).key).toEqual(routesByIdsQuery({ feedCode: 'f', routeIds: ['a', 'b'] }).key)
  })

  it('splits 150 ids into two requests of at most 100 and merges them', async () => {
    const ids = Array.from({ length: 150 }, (_, i) => `id${String(i).padStart(3, '0')}`)
    const { calls } = stubFetch(({ url }) => page(new URL(url).searchParams.getAll('routeId').map((routeId) => ({ routeId }))))
    const rows = await runQuery(routesByIdsQuery({ feedCode: 'f', routeIds: ids }))
    expect(calls).toHaveLength(2)
    const sizes = calls.map(({ url }) => new URL(url).searchParams.getAll('routeId').length)
    expect(Math.max(...sizes)).toBeLessThanOrEqual(100)
    expect(rows.map((r) => r.routeId)).toEqual(ids)
  })

  it('reads every page of a chunk', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ routeId: 'a' }, { routeId: 'b' }] : [{ routeId: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(routesByIdsQuery({ feedCode: 'f', routeIds: ['a', 'b', 'c'] }))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('resolves [] without a request for an empty list', async () => {
    const { calls } = stubFetch(() => page([]))
    expect(await runQuery(routesByIdsQuery({ feedCode: 'f', routeIds: [] }))).toEqual([])
    expect(calls).toHaveLength(0)
  })
})
