import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { claimDraftEditorMutation } from '~/composables/api/mutations/drafts'
import { fakeQueryCache } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { path: { id: '9' }, body: { editor: 'e' } }

describe('claimDraftEditorMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends POST /api/v1/drafts/9/editor-lock/claim and returns the result', async () => {
    const { calls } = stubFetch(() => json({ editor: 'e' }))
    const options = claimDraftEditorMutation()
    expect(await options.mutation(vars as never)).toEqual({ editor: 'e' })
    expect(calls[0]!.method).toBe('POST')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/drafts/9/editor-lock/claim')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('has no onSuccess (lock state comes back in the response)', () => {
    expect('onSuccess' in claimDraftEditorMutation()).toBe(false)
  })

  it('does not invalidate when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    const queryCache = fakeQueryCache()
    const options = claimDraftEditorMutation()
    await expect(options.mutation(vars as never)).rejects.toMatchObject({ status: 409 })
    await expect(options.mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
    expect(queryCache.invalidateQueries).not.toHaveBeenCalled()
  })
})
