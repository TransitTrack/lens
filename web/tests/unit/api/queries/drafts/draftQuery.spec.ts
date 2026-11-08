import { afterEach, describe, expect, it, vi } from 'vitest'
import { draftKeys } from '~/composables/api/keys/drafts'
import { draftQuery } from '~/composables/api/queries/drafts/draftQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('draftQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /drafts/7 and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 7 }))
    const options = draftQuery({ id: 7 })
    expect(await runQuery(options)).toEqual({ id: 7 })
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/drafts/7')
    expect(options.key).toEqual(['drafts', '7', 'detail', {}])
    expect(options.key.slice(0, 2)).toEqual(draftKeys.draft('7'))
    expect(options.gcTime).toBe(0)
    expect(options.staleTime).toBe(0)
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(draftQuery({ id: 7 }))).toBeNull()
  })
})
