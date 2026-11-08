import { afterEach, describe, expect, it, vi } from 'vitest'
import { stopTimesQuery } from '~/composables/api/queries/gtfs/stopTimesQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, tripId: 't1' }

describe('stopTimesQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /trips/t1/stop-times with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = stopTimesQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/trips/t1/stop-times')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'stop-times', {tripId: 't1'}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(stopTimesQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(stopTimesQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
