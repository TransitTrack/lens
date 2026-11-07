import { afterEach, describe, expect, it, vi } from 'vitest'
import { useUpdateFeedMutation } from '../../app/composables/useRestManagement'

describe('REST feed management', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('updates a feed with the generated request body', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ code: 'f', name: 'Feed', url: 'https://example.org', enabled: true, source: 'API' })))
    vi.stubGlobal('fetch', fetcher)
    const result = await useUpdateFeedMutation().mutate({ variables: { code: 'f', input: { name: 'Feed', url: 'https://example.org' } } })
    expect(result.data.updateFeed.code).toBe('f')
    expect(fetcher.mock.calls[0]![0]).toContain('/api/v1/feeds/f')
    expect(JSON.parse(String(fetcher.mock.calls[0]![1]?.body)).name).toBe('Feed')
  })
})
