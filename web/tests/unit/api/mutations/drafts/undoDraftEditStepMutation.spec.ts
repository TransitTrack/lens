import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { undoDraftEditStepMutation } from '~/composables/api/mutations/drafts'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { path: { id: '9' }, body: { editor: 'e', expectedVersion: 3 } }

describe('undoDraftEditStepMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends POST /api/v1/drafts/9/undo and returns the result', async () => {
    const { calls } = stubFetch(() => json({ canUndo: true, canRedo: false }))
    const options = undoDraftEditStepMutation()
    expect(await options.mutation(vars as never)).toEqual({ canUndo: true, canRedo: false })
    expect(calls[0]!.method).toBe('POST')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/drafts/9/undo')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('has no success hook, so a step invalidates nothing', () => {
    expect('onSuccess' in undoDraftEditStepMutation()).toBe(false)
  })

  it('rejects with ApiError when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    await expect(undoDraftEditStepMutation().mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
  })
})
