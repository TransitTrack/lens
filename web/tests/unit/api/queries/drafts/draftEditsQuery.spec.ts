import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftKeys } from '~/composables/api/keys/drafts'
import { draftEditsQuery } from '~/composables/api/queries/drafts/draftEditsQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('draftEditsQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /drafts/7/edits with the limit and returns the rows', async () => {
    const { calls } = stubFetch(() => json([{ seq: 1 }]))
    const options = draftEditsQuery({ id: 7, limit: 20 })
    expect(await runQuery(options)).toEqual([{ seq: 1 }])
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/drafts/7/edits')
    expect(url.searchParams.get('limit')).toBe('20')
    expect(options.key).toEqual(['drafts', '7', 'edits', { limit: 20 }])
    expect(options.key.slice(0, 2)).toEqual(draftKeys.draft('7'))
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })

  it('sends no limit when unset', async () => {
    const { calls } = stubFetch(() => json([]))
    await runQuery(draftEditsQuery({ id: 7 }))
    expect(new URL(calls[0]!.url).searchParams.has('limit')).toBe(false)
  })
})
