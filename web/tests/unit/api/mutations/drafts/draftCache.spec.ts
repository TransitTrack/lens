import { describe, expect, it, vi } from 'vitest'
import { cancelDraftReads, invalidateDraft } from '~/composables/api/mutations/drafts'
import type { QueryCache } from '~/composables/api/mutations/types'
import { fakeQueryCache } from '../../../../helpers/colada'

describe('invalidateDraft', () => {
  it('invalidates every read of the draft once', async () => {
    const queryCache = fakeQueryCache()
    await invalidateDraft(queryCache, 9)
    expect(queryCache.invalidateQueries).toHaveBeenCalledTimes(1)
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['drafts', '9'] })
  })

  it('swallows a failed refetch', async () => {
    const queryCache = fakeQueryCache()
    queryCache.invalidateQueries.mockRejectedValueOnce(new Error('aborted'))
    await expect(invalidateDraft(queryCache, 9)).resolves.toBeUndefined()
  })
})

describe('cancelDraftReads', () => {
  it("cancels every in-flight read under the draft's key", () => {
    const cancelQueries = vi.fn()
    cancelDraftReads({ cancelQueries } as unknown as QueryCache, 9)
    expect(cancelQueries).toHaveBeenCalledWith({ key: ['drafts', '9'] })
  })
})
