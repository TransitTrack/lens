import { afterEach, describe, expect, it, vi } from 'vitest'
import { feedQuery } from '~/composables/api/queries/gtfs/feedQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

describe('feedQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feeds/{code} and returns the feed', async () => {
    const { calls } = stubFetch(() => json({ code: 'f' }))
    const options = feedQuery({ code: 'f' })
    expect(await runQuery(options)).toEqual({ code: 'f' })
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/feeds/f')
    expect(options.key).toEqual(['feeds', 'f'])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(feedQuery({ code: 'f' }))).toBeNull()
  })
})
