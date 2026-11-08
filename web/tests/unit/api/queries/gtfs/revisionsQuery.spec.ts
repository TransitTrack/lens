import { afterEach, describe, expect, it, vi } from 'vitest'
import { revisionsQuery } from '~/composables/api/queries/gtfs/revisionsQuery'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('revisionsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests the feed revisions filtered by status and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 1 }]))
    const options = revisionsQuery({ feedCode: 'f', status: 'ACTIVE' })
    expect(await runQuery(options)).toEqual([{ id: 1 }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/revisions')
    expect(url.searchParams.get('status')).toBe('ACTIVE')
    expect(options.key).toEqual(['feeds', 'f', 'revisions', { status: 'ACTIVE' }])
  })

  it('omits the status when not given', async () => {
    const { calls } = stubFetch(() => page([]))
    const options = revisionsQuery({ feedCode: 'f' })
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.has('status')).toBe(false)
    expect(options.key).toEqual(['feeds', 'f', 'revisions', {}])
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 1 }, { id: 2 }] : [{ id: 3 }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(revisionsQuery({ feedCode: 'f' }))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })
})
