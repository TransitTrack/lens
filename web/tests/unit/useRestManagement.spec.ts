import { afterEach, describe, expect, it, vi } from 'vitest'
import { useUpdateFeedMutation } from '../../app/composables/useRestManagement'
import { json, stubFetch } from '../helpers/stubFetch'

describe('REST feed management', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('updates a feed with the generated request body', async () => {
    const { calls } = stubFetch(() => json({ code: 'f', name: 'Feed', url: 'https://example.org', enabled: true, source: 'API' }))
    const result = await useUpdateFeedMutation().mutate({ variables: { code: 'f', input: { name: 'Feed', url: 'https://example.org' } } })
    expect(result.data.updateFeed.code).toBe('f')
    expect(calls[0]!.url).toContain('/api/v1/feeds/f')
    expect((calls[0]!.body as { name: string }).name).toBe('Feed')
  })
})
