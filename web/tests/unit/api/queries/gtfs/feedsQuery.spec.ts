import { afterEach, describe, expect, it, vi } from 'vitest'
import { feedsQuery } from '~/composables/api/queries/gtfs'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('feedsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ code: 'a' }]))
    expect(await runQuery(feedsQuery)).toEqual([{ code: 'a' }])
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/feeds')
    expect(feedsQuery.key).toEqual(['feeds', { list: true }])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ code: 'a' }, { code: 'b' }] : [{ code: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(feedsQuery)).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
