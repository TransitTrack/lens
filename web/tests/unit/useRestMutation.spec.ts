import { describe, expect, it } from 'vitest'
import { ApiError } from '../../app/api'
import { useRestMutation } from '../../app/composables/useRestMutation'

describe('useRestMutation', () => {
  it('clears the loading flag and passes the error through', async () => {
    const problem = { status: 409, title: 'Conflict', code: 'STALE_DRAFT', currentVersion: 5 }
    const mutation = useRestMutation(async (_v: { id: string }) => { throw new ApiError(409, problem) })
    try {
      await mutation.mutate({ variables: { id: '1' } })
      throw new Error('expected a conflict')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).status).toBe(409)
      expect((error as ApiError).problem?.code).toBe('STALE_DRAFT')
      expect((error as ApiError).problem?.currentVersion).toBe(5)
    }
    expect(mutation.loading.value).toBe(false)
  })
})
