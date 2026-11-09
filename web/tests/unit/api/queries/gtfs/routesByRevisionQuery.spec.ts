import { afterEach, describe, expect, it, vi } from 'vitest'
import { routesByRevisionQuery } from '~/composables/api/queries/gtfs/routesByRevisionQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('routesByRevisionQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reads each revision\'s routes by id, pinned to that revision, and keeps them apart', async () => {
    const { calls } = stubFetch(({ url }) => {
      const u = new URL(url)
      return page(u.searchParams.getAll('routeId').map((routeId) => ({ routeId, routeShortName: `${routeId}@${u.searchParams.get('revisionId')}` })))
    })
    const options = routesByRevisionQuery({ feedCode: 'f', routeIdsByRevision: { 2: ['b', 'a', 'a'], 1: ['a'] } })
    const result = await runQuery(options)
    const urls = calls.map((c) => new URL(c.url))
    expect(urls.map((u) => `${u.pathname}?${u.searchParams.get('revisionId')}:${u.searchParams.getAll('routeId').join(',')}`).sort()).toEqual([
      '/api/v1/feeds/f/routes?1:a',
      '/api/v1/feeds/f/routes?2:a,b',
    ])
    expect(result.get('1')!.get('a')!.routeShortName).toBe('a@1')
    expect(result.get('2')!.get('a')!.routeShortName).toBe('a@2')
    expect(result.get('2')!.get('b')!.routeShortName).toBe('b@2')
    expect(options.key).toEqual(['feeds', 'f', 'routes-by-revision', { revisions: [['1', ['a']], ['2', ['a', 'b']]] }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('splits more than 100 ids of one revision into several requests', async () => {
    const { calls } = stubFetch(() => page([]))
    const ids = Array.from({ length: 150 }, (_, i) => `r${String(i).padStart(3, '0')}`)
    await runQuery(routesByRevisionQuery({ feedCode: 'f', routeIdsByRevision: { 7: ids } }))
    expect(calls.map((c) => new URL(c.url).searchParams.getAll('routeId').length)).toEqual([100, 50])
  })

  it('gives the same key for the same ids in any order and skips empty revisions', () => {
    const a = routesByRevisionQuery({ feedCode: 'f', routeIdsByRevision: { 1: ['b', 'a'], 2: [] } }).key
    const b = routesByRevisionQuery({ feedCode: 'f', routeIdsByRevision: { 1: ['a', 'b'] } }).key
    expect(a).toEqual(b)
  })

  it('makes no request without ids', async () => {
    const { calls } = stubFetch(() => page([]))
    const result = await runQuery(routesByRevisionQuery({ feedCode: 'f', routeIdsByRevision: {} }))
    expect(calls).toHaveLength(0)
    expect(result.size).toBe(0)
  })
})
