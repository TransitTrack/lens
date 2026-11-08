import { afterEach, describe, expect, it, vi } from 'vitest'
import { calendarsQuery } from '~/composables/api/queries/gtfs/calendarsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7 }

describe('calendarsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /calendars with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = calendarsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/calendars')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'calendars', {}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(calendarsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(calendarsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
