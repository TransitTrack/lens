import { afterEach, describe, expect, it, vi } from 'vitest'
import { avlFeedsQuery } from '~/composables/api/queries/avl/avlFeedsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('avlFeedsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /avl-feeds and returns the result', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    expect(await runQuery(avlFeedsQuery)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/avl-feeds')
    expect(avlFeedsQuery.key).toEqual(['avl-feeds'])
    expect(avlFeedsQuery.staleTime).toBe(60_000)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(avlFeedsQuery)).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
