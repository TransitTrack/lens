import { afterEach, describe, expect, it, vi } from 'vitest'
import { feedInfoQuery } from '~/composables/api/queries/gtfs/feedInfoQuery'
import { runQuery } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const params = { feedCode: 'f', revisionId: 7 }

describe('feedInfoQuery', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests /feed-info with the scope and returns the row', async () => {
    const { calls } = stubFetch(() => json({ id: 'x' }))
    const options = feedInfoQuery(params)
    expect(await runQuery(options)).toEqual({ id: 'x' })
    const url = new URL(calls[0]!.url)
    expect(url.pathname).toBe('/api/v1/feeds/f/feed-info')
    expect(url.searchParams.get('revisionId')).toBe('7')
    expect(options.key).toEqual(['feeds', 'f', 'rev', '7', 'feed-info', {}])
  })

  it('returns null on 404', async () => {
    stubFetch(() => json({ title: 'Not Found', status: 404 }, 404))
    expect(await runQuery(feedInfoQuery(params))).toBeNull()
  })

  it('sends no revisionId for the active revision', async () => {
    const { calls } = stubFetch(() => json({}))
    await runQuery(feedInfoQuery({ ...params, revisionId: undefined }))
    expect(new URL(calls[0]!.url).searchParams.has('revisionId')).toBe(false)
  })
})
