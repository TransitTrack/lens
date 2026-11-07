import { afterEach, describe, expect, it, vi } from 'vitest'
import { useStartOptimizationRunMutation } from '../../app/composables/useRestOptimization'

describe('REST optimization', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('starts a run using the generated client', async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ id: '42', revisionId: '7', state: 'QUEUED', createdAt: 'now' })))
    vi.stubGlobal('fetch', fetcher)
    const result = await useStartOptimizationRunMutation().mutate({ variables: {
      input: { feedCode: 'f', observedFrom: '2026-01-01T00:00:00Z', observedTo: '2026-01-02T00:00:00Z', minimumSamples: 10 },
    } })
    expect(result.data.startOptimizationRun.id).toBe('42')
    expect(fetcher.mock.calls[0]![0]).toContain('/api/v1/optimization-runs')
    expect(JSON.parse(String(fetcher.mock.calls[0]![1]?.body)).feedCode).toBe('f')
  })
})
