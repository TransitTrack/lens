import { afterEach, describe, expect, it, vi } from 'vitest'
import { useShiftTripMutation } from '../../app/composables/useRestDraftMutations'
import { RestProblemError } from '../../app/composables/useRestMutation'

describe('REST draft edits', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('posts the edit and returns the updated draft', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ draft: { id: '1', version: 4 }, canUndo: true, canRedo: false })))
    vi.stubGlobal('fetch', fetcher)
    const { mutate } = useShiftTripMutation()
    const result = await mutate({ variables: { input: { draftId: '1', editor: 'alice', expectedVersion: 3, tripId: 't', deltaSec: 60 } } })
    expect(result.data.shiftTrip.draft.version).toBe(4)
    expect(fetcher.mock.calls[0]![0]).toContain('/api/v1/draft-edit-commands/shift-trip')
    expect(JSON.parse(String(fetcher.mock.calls[0]![1]?.body)).expectedVersion).toBe(3)
  })
  it('preserves stale edit errors', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ title: 'Conflict', status: 409, code: 'STALE_DRAFT' }), { status: 409 })))
    const { mutate } = useShiftTripMutation()
    await expect(mutate({ variables: { input: { draftId: '1', editor: 'alice', expectedVersion: 3, tripId: 't', deltaSec: 60 } } })).rejects.toBeInstanceOf(RestProblemError)
  })
})
