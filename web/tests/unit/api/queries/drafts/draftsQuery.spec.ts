import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftsQuery } from '~/composables/api/queries/drafts'
import { runQuery } from '../../../../helpers/colada'
import { page, stubFetch } from '../../../../helpers/stubFetch'

describe('draftsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /drafts filtered by feed and returns the rows', async () => {
    const { calls } = stubFetch(() => page([{ id: 'x' }]))
    const options = draftsQuery({ feedCode: 'f' })
    expect(await runQuery(options)).toEqual([{ id: 'x' }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/drafts')
    expect(url.searchParams.get('feedCode')).toBe('f')
    expect(options.key).toEqual(['drafts', 'list', { feedCode: 'f' }])
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })

  it('reads every page', async () => {
    const { calls } = stubFetch(({ url }) => new Response(
      JSON.stringify(new URL(url).searchParams.get('offset') === '0' ? [{ id: 'a' }, { id: 'b' }] : [{ id: 'c' }]),
      { headers: { 'Content-Type': 'application/json', 'X-Total-Count': '3' } },
    ))
    expect(await runQuery(draftsQuery({}))).toHaveLength(3)
    expect(calls).toHaveLength(2)
  })

  it('sends no feedCode when unset', async () => {
    const { calls } = stubFetch(() => page([]))
    const options = draftsQuery({})
    await runQuery(options)
    expect(new URL(calls[0]!.url).searchParams.has('feedCode')).toBe(false)
    expect(options.key).toEqual(['drafts', 'list', {}])
  })
})
