import { describe, expect, it } from 'vitest'
import { ResponseError } from '../../generated/rest'
import { RestProblemError, useRestMutation } from '../../app/composables/useRestMutation'

describe('useRestMutation', () => {
  it('preserves conflict codes and version details', async () => {
    const response = new Response(JSON.stringify({ status: 409, title: 'Conflict', code: 'STALE_DRAFT', currentVersion: 5 }), { status: 409 })
    const mutation = useRestMutation(async (_v: { id: string }) => { throw new ResponseError(response) })
    try {
      await mutation.mutate({ variables: { id: '1' } })
      throw new Error('expected a conflict')
    } catch (error) {
      expect(error).toBeInstanceOf(RestProblemError)
      expect((error as RestProblemError).problem.code).toBe('STALE_DRAFT')
      expect((error as RestProblemError).problem.currentVersion).toBe(5)
    }
    expect(mutation.loading.value).toBe(false)
  })
})
