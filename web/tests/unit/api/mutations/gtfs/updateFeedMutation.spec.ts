import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '~/api'
import { updateFeedMutation } from '~/composables/api/mutations/gtfs'
import { fakeQueryCache } from '../../../../helpers/colada'
import { json, stubFetch } from '../../../../helpers/stubFetch'

const vars = { path: { code: 'f' }, body: { name: 'New' } as never }

describe('updateFeedMutation', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends PATCH /api/v1/feeds/f and returns the result', async () => {
    const { calls } = stubFetch(() => json({ code: 'f' }))
    const queryCache = fakeQueryCache()
    const options = updateFeedMutation(queryCache)
    expect(await options.mutation(vars as never)).toEqual({ code: 'f' })
    expect(calls[0]!.method).toBe('PATCH')
    expect(new URL(calls[0]!.url).pathname).toBe('/api/v1/feeds/f')
    expect(calls[0]!.body).toEqual(vars.body)
  })

  it('invalidates on success', async () => {
    stubFetch(() => json({ code: 'f' }))
    const queryCache = fakeQueryCache()
    const options = updateFeedMutation(queryCache)
    const data = await options.mutation(vars as never)
    // Pinia Colada runs onSuccess after mutation resolves; do the same here.
    options.onSuccess(data as never, vars as never)
    expect(queryCache.invalidateQueries).toHaveBeenCalledTimes(1)
    expect(queryCache.invalidateQueries).toHaveBeenCalledWith({ key: ['feeds'] })
  })

  it('does not invalidate when the request fails', async () => {
    stubFetch(() => json({ title: 'Conflict', status: 409 }, 409))
    const queryCache = fakeQueryCache()
    const options = updateFeedMutation(queryCache)
    await expect(options.mutation(vars as never)).rejects.toMatchObject({ status: 409 })
    await expect(options.mutation(vars as never)).rejects.toBeInstanceOf(ApiError)
    expect(queryCache.invalidateQueries).not.toHaveBeenCalled()
  })
})
