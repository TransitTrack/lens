import { afterEach, describe, expect, it, vi } from 'vitest'
import { stopsByIdsQuery } from '~/composables/api/queries/gtfs/stopsByIdsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('stopsByIdsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends the sorted, de-duplicated ids and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ stopId: 'a' }]))
    const options = stopsByIdsQuery({ feedCode: 'f', revisionId: 7, stopIds: ['b', 'a', 'b'] })
    expect(await runQuery(options)).toEqual([{ stopId: 'a' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/stops')
    expect(url.searchParams.getAll('stopId')).toEqual(['a', 'b'])
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'stops', { stopIds: ['a', 'b'] }])
  })

  it('gives the same key for the same ids in any order', () => {
    expect(stopsByIdsQuery({ feedCode: 'f', stopIds: ['b', 'a'] }).key).toEqual(stopsByIdsQuery({ feedCode: 'f', stopIds: ['a', 'b'] }).key)
  })

  it('splits 150 ids into two requests of at most 100 and merges them', async () => {
    const ids = Array.from({ length: 150 }, (_, i) => `id${String(i).padStart(3, '0')}`)
    const { calls } = stubFetch(({ url }) => page(new URL(url).searchParams.getAll('stopId').map((stopId) => ({ stopId }))))
    const rows = await runQuery(stopsByIdsQuery({ feedCode: 'f', stopIds: ids }))
    expect(calls).toHaveLength(2)
    const sizes = calls.map(({ url }) => new URL(url).searchParams.getAll('stopId').length)
    expect(Math.max(...sizes)).toBeLessThanOrEqual(100)
    expect(rows.map((r) => r.stopId)).toEqual(ids)
  })

  it('reads every page of a chunk', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ stopId: 'a' }, { stopId: 'b' }] : [{ stopId: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(stopsByIdsQuery({ feedCode: 'f', stopIds: ['a', 'b', 'c'] }))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('resolves [] without a request for an empty list', async () => {
    const { calls } = stubFetch(() => page([]))
    expect(await runQuery(stopsByIdsQuery({ feedCode: 'f', stopIds: [] }))).toEqual([])
    expect(calls).toHaveLength(0)
  })
})
