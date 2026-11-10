import { afterEach, describe, expect, it, vi } from 'vitest'
import { stopPathsQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, routeId: 'r1', patternKey: 'pk' }

describe('stopPathsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /stop-paths with the scope and returns raw rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = stopPathsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/stop-paths')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(url.searchParams.get('patternKey')).toBe('pk')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'stop-paths', { routeId: 'r1', patternKey: 'pk' }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(stopPathsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('is fresh for 60 s on the active revision and sends no revisionId', async () => {
    const { calls } = stubFetch(() => page([]))
    const options = stopPathsQuery({ ...params, revisionId: undefined })
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
    expect(options.staleTime).toBe(60_000)
  })

  it('reads a draft: key under drafts, never cached', async () => {
    const { calls } = stubFetch(() => page([]))
    const options = stopPathsQuery({ feedCode: 'f', draftId: 9 })
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.get('revisionId')).toBe('9')
    expect(options.key.slice(0, 2)).toEqual(['drafts', '9'])
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })
})
