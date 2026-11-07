import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useRestQuery } from '../../app/composables/useRestQuery'

describe('useRestQuery', () => {
  it('runs when enabled and updates on variables change', async () => {
    const enabled = ref(false)
    const id = ref('a')
    const load = vi.fn(async (value: { id: string }) => ({ value: value.id }))
    const scope = effectScope()
    const query = scope.run(() => useRestQuery(() => ({ variables: { id: id.value }, enabled: enabled.value }), load))!
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
    const query = scope.run(() => useRestQuery(() => ({ variables: { id: 'a' } }), async () => { throw new Error('network') }))!
    const onError = vi.fn()
    query.onError(onError)
    await vi.waitFor(() => expect(onError).toHaveBeenCalledWith(expect.objectContaining({ message: 'network' })))
    scope.stop()
  })
})
