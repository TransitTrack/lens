import { afterEach, describe, expect, it, vi } from 'vitest'
import { tripPatternsQuery } from '~/composables/api/queries/schedule'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, routeId: 'r1' }

describe('tripPatternsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests trip-patterns with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = tripPatternsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/trip-patterns')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('routeId')).toBe('r1')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'trip-patterns', { routeId: 'r1' }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(tripPatternsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(tripPatternsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
