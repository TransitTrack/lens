import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { discardDraftMutation } from '~/composables/api/mutations/drafts'
import { fakeQueryCache } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { path: { id: '9' }, body: { editor: 'e' } }

describe('discardDraftMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends DELETE /api/v1/drafts/9 and returns the result', async () => {
    const { calls } = stubFetch(() => json(true))
    const queryCache = fakeQueryCache()
    const options = discardDraftMutation(queryCache)
    expect(await options.mutation(vars as never)).toEqual(true)
    expect(calls[0]!.method).toBe('DELETE')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/drafts/9')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('invalidates on success', async () => {
    stubFetch(() => json(true))
    const queryCache = fakeQueryCache()
    const options = discardDraftMutation(queryCache)
    const data = await options.mutation(vars as never)
    // Pinia Colada runs onSuccess after mutation resolves; do the same here.
    options.onSuccess(data as never, vars as never)
    expect(queryCache.invalidateQueries).toHaveBeenCalledTimes(1)
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['drafts'] })
  })

  it('does not invalidate when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    const queryCache = fakeQueryCache()
    const options = discardDraftMutation(queryCache)
    await expect(options.mutation(vars as never)).rejects.toMatchObject({ status: 409 })
    await expect(options.mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
    expect(queryCache.invalidateQueries).not.toHaveBeenCalled()
  })
})
