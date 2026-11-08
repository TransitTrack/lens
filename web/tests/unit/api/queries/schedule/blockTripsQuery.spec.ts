import { afterEach, describe, expect, it, vi } from 'vitest'
import { blockTripsQuery } from '~/composables/api/queries/schedule/blockTripsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7, blockId: 'b1', serviceId: 's1' }

describe('blockTripsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests block-trips with the scope and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = blockTripsQuery(params)
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/block-trips')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(url.searchParams.get('blockId')).toBe('b1'); expect(url.searchParams.get('serviceId')).toBe('s1')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'block-trips', { blockId: 'b1', serviceId: 's1' }])
    expect(options.staleTime).toBe(Infinity)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(blockTripsQuery(params))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => page([]))
    await runQuery(blockTripsQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
