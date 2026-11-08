import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { updateStopTimeMutation } from '~/composables/api/mutations/drafts/updateStopTimeMutation'
import { fakeQueryCache } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { body: { draftId: '9', editor: 'e' } as never }

describe('updateStopTimeMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends POST /api/v1/draft-edit-commands/update-stop-time and returns the result', async () => {
    const { calls } = stubFetch(() => json({ canUndo: true, canRedo: false }))
    const queryCache = fakeQueryCache()
    const options = updateStopTimeMutation(queryCache)
    expect(await options.mutation(vars as never)).toEqual({ canUndo: true, canRedo: false })
    expect(calls[0]!.method).toBe('POST')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/draft-edit-commands/update-stop-time')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('invalidates on success', async () => {
    stubFetch(() => json({ canUndo: true, canRedo: false }))
    const queryCache = fakeQueryCache()
    const options = updateStopTimeMutation(queryCache)
    const data = await options.mutation(vars as never)
    // Pinia Colada runs onSuccess after mutation resolves; do the same here.
    options.onSuccess(data as never, vars as never)
    expect(queryCache.invalidateQueries).toHaveBeenCalledTimes(1)
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['drafts', '9'] })
  })

  it('does not invalidate when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    const queryCache = fakeQueryCache()
    const options = updateStopTimeMutation(queryCache)
    await expect(options.mutation(vars as never)).rejects.toMatchObject({ status: 409 })
    await expect(options.mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
    expect(queryCache.invalidateQueries).not.toHaveBeenCalled()
  })
})
