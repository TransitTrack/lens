import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../app/api'
import { useShiftTripMutation } from '../../app/composables/useRestDraftMutations'
import { json, stubFetch } from '../helpers/stubFetch'

const input = { draftId: '1', editor: 'alice', expectedVersion: 3, tripId: 't', deltaSec: 60 }

describe('REST draft edits', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('posts the edit and returns the updated draft', async () => {
    const { calls } = stubFetch(() => json({ draft: { id: '1', version: 4 }, canUndo: true, canRedo: false }))
    const { mutate } = useShiftTripMutation()
    const result = await mutate({ variables: { input } })
    expect(result.data.shiftTrip.draft.version).toBe(4)
    expect(calls[0]!.url).toContain('/api/v1/draft-edit-commands/shift-trip')
    expect(calls[0]!.method).toBe('POST')
    expect((calls[0]!.body as { expectedVersion: number }).expectedVersion).toBe(3)
  })
  it('preserves stale edit errors', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409, code: 'STALE_DRAFT', currentVersion: 5 }, 409))
    const { mutate } = useShiftTripMutation()
    const error = await mutate({ variables: { input } }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).problem).toMatchObject({ code: 'STALE_DRAFT', currentVersion: 5 })
  })
  it('keeps the status when the error body is not a problem document', async () => {
    stubFetch(() => new Response('gateway down', { status: 502 }))
    const error = await useShiftTripMutation().mutate({ variables: { input } }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(502)
    expect((error as ApiError).problem).toBeUndefined()
  })
})
