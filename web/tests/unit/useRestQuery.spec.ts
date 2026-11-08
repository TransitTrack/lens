import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useRestQuery } from '../../app/composables/useRestQuery'

describe('useRestQuery', () => {
  it('runs when enabled and updates on variables change', async () => {
    const enabled = ref(false)
    const id = ref('a')
    const load = vi.fn(async (value: { id: string }) => ({ value: value.id }))
    const scope = effectScope()
    const query = scope.run(() => useRestQuery('test', () => ({ variables: { id: id.value }, enabled: enabled.value }), load))!
    await nextTick()
    expect(load).not.toHaveBeenCalled()
    enabled.value = true
    await vi.waitFor(() => expect(query.result.value).toEqual({ value: 'a' }))
    id.value = 'b'
    await vi.waitFor(() => expect(query.result.value).toEqual({ value: 'b' }))
    scope.stop()
  })
  it('notifies error subscribers when a request fails', async () => {
    const scope = effectScope()
    const query = scope.run(() => useRestQuery('test', () => ({ variables: { id: 'a' } }), async () => { throw new Error('network') }))!
    const onError = vi.fn()
    query.onError(onError)
    await vi.waitFor(() => expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: 'network' })))
    scope.stop()
  })

  it('shares one request between components reading the same key and variables', async () => {
    const load = vi.fn(async (value: { id: string }) => ({ value: value.id }))
    const scope = effectScope()
    const [first, second] = scope.run(() => [
      useRestQuery('shared', () => ({ variables: { id: 'a' } }), load),
      useRestQuery('shared', () => ({ variables: { id: 'a' } }), load),
    ])!
    await vi.waitFor(() => expect(second.result.value).toEqual({ value: 'a' }))
    expect(first.result.value).toEqual({ value: 'a' })
    expect(load).toHaveBeenCalledTimes(1)
    scope.stop()
  })
  it('keeps the previous result visible while new variables load', async () => {
    const id = ref('a')
    let release!: () => void
    const gate = new Promise<void>((resolve) => { release = resolve })
    const load = vi.fn(async (value: { id: string }) => { if (value.id === 'b') await gate; return { value: value.id } })
    const scope = effectScope()
    const query = scope.run(() => useRestQuery('keep', () => ({ variables: { id: id.value } }), load))!
    await vi.waitFor(() => expect(query.result.value).toEqual({ value: 'a' }))
    id.value = 'b'
    await vi.waitFor(() => expect(query.loading.value).toBe(true))
    expect(query.result.value).toEqual({ value: 'a' })
    release()
    await vi.waitFor(() => expect(query.result.value).toEqual({ value: 'b' }))
    scope.stop()
  })
  it('polls at the requested interval', async () => {
    const load = vi.fn(async () => ({ at: Date.now() }))
    const scope = effectScope()
    scope.run(() => useRestQuery('poll', () => ({ variables: {}, pollInterval: 20 }), load))
    await vi.waitFor(() => expect(load.mock.calls.length).toBeGreaterThanOrEqual(3))
    scope.stop()
  })
  it('refetch resolves with the fresh result, or undefined when the read fails', async () => {
    let n = 0
    let fail = false
    const load = vi.fn(async () => { if (fail) throw new Error('boom'); return { n: ++n } })
    const scope = effectScope()
    const query = scope.run(() => useRestQuery('refetch', () => ({ variables: {} }), load))!
    await vi.waitFor(() => expect(query.result.value).toEqual({ n: 1 }))
    expect(await query.refetch()).toEqual({ result: { n: 2 } })
    fail = true
    expect(await query.refetch()).toBeUndefined()
    expect(query.error.value?.message).toBe('boom')
    scope.stop()
  })
})
