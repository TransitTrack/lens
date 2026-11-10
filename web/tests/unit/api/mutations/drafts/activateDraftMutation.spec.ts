import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { activateDraftMutation } from '~/composables/api/mutations/drafts'
import { fakeQueryCache } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { path: { id: '9' }, body: { editor: 'e' } }

describe('activateDraftMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends POST /api/v1/drafts/9/activation and returns the result', async () => {
    const { calls } = stubFetch(() => json({ id: 1 }))
    const queryCache = fakeQueryCache()
    const options = activateDraftMutation(queryCache)
    expect(await options.mutation(vars as never)).toEqual({ id: 1 })
    expect(calls[0]!.method).toBe('POST')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/drafts/9/activation')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('invalidates on success', async () => {
    stubFetch(() => json({ id: 1 }))
    const queryCache = fakeQueryCache()
    const options = activateDraftMutation(queryCache)
    const data = await options.mutation(vars as never)
    // Pinia Colada runs onSuccess after mutation resolves; do the same here.
    options.onSuccess(data as never, vars as never)
    expect(queryCache.invalidateQueries).toHaveBeenCalledTimes(2)
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['drafts'] })
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['feeds'] })
  })

  it('does not invalidate when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    const queryCache = fakeQueryCache()
    const options = activateDraftMutation(queryCache)
    await expect(options.mutation(vars as never)).rejects.toMatchObject({ status: 409 })
    await expect(options.mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
    expect(queryCache.invalidateQueries).not.toHaveBeenCalled()
  })
})
