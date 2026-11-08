import { afterEach, describe, expect, it, vi } from 'vitest'
import { routeStopsQuery } from '~/composables/api/queries/gtfs/routeStopsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, routeId: 'r1' }

describe('routeStopsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /routes/r1/stops with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = routeStopsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/routes/r1/stops')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'route-stops', {routeId: 'r1'}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(routeStopsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(routeStopsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
