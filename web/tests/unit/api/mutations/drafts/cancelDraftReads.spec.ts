import { describe, expect, it, vi } from 'vitest'
import { cancelDraftReads } from '~/composables/api/mutations/drafts/cancelDraftReads'
import type { QueryCache } from '~/composables/api/mutations/types'

describe('cancelDraftReads', () => {
  it("cancels every in-flight read under the draft's key", () => {
    const cancelQueries = vi.fn()
    cancelDraftReads({ cancelQueries } as unknown as QueryCache, 9)
    expect(cancelQueries).toHaveBeenCalledWith({ key: ['drafts', '9'] })
  })
})
