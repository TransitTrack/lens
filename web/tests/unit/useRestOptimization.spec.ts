import { afterEach, describe, expect, it, vi } from 'vitest'
import { useStartOptimizationRunMutation } from '../../app/composables/useRestOptimization'
import { json, stubFetch } from '../helpers/stubFetch'

describe('REST optimization', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('starts a run using the generated client', async () => {
    const { calls } = stubFetch(() => json({ id: '42', revisionId: '7', state: 'QUEUED', createdAt: 'now' }))
    const result = await useStartOptimizationRunMutation().mutate({ variables: {
      input: { feedCode: 'f', observedFrom: '2026-01-01T00:00:00Z', observedTo: '2026-01-02T00:00:00Z', minimumSamples: 10 },
    } })
    expect(result.data.startOptimizationRun.id).toBe('42')
    expect(calls[0]!.url).toContain('/api/v1/optimization-runs')
    expect((calls[0]!.body as { feedCode: string }).feedCode).toBe('f')
  })
})
