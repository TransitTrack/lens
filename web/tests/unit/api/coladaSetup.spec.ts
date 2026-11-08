import { afterEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { useQuery } from '@pinia/colada'
import { defineComponent, h, nextTick, ref, type Ref } from 'vue'

const flush = async () => { for (let i = 0; i < 5; i++) await Promise.resolve(); await nextTick() }

afterEach(() => { vi.useRealTimers() })

describe('Pinia Colada setup', () => {
  it('fetches once per key and shares the entry', async () => {
    const query = vi.fn(async () => 'value')
    const Probe = defineComponent({
      setup() {
        useQuery({ key: ['t'], query })
        useQuery({ key: ['t'], query })
        return () => h('div')
      },
    })
    const wrapper = await mountSuspended(Probe)
    await flush()
    expect(query).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('auto-refetches at the given interval and stops when set to false', async () => {
    vi.useFakeTimers()
    const interval: Ref<number | false> = ref(1000)
    const query = vi.fn(async () => 'value')
    const Probe = defineComponent({
      setup() {
        useQuery({ key: ['poll'], query, autoRefetch: () => interval.value })
        return () => h('div')
      },
    })
    const wrapper = await mountSuspended(Probe)
    await vi.advanceTimersByTimeAsync(0)
    expect(query).toHaveBeenCalledTimes(1)
    for (let i = 0; i < 3; i++) await vi.advanceTimersByTimeAsync(1000)
    expect(query).toHaveBeenCalledTimes(4)
    interval.value = false
    // The plugin re-reads `autoRefetch` only after a fetch, so the tick already scheduled still
    // fires once; the next schedule sees `false` and polling stops.
    await vi.advanceTimersByTimeAsync(1000)
    expect(query).toHaveBeenCalledTimes(5)
    await vi.advanceTimersByTimeAsync(5000)
    expect(query).toHaveBeenCalledTimes(5)
    wrapper.unmount()
  })

  it('keeps previous data while the next key loads', async () => {
    const id = ref('a')
    let resolveB: (v: string) => void = () => {}
    const query = vi.fn((): Promise<string> =>
      id.value === 'a' ? Promise.resolve('data-a') : new Promise((r) => { resolveB = r }))
    let data!: Ref<string | undefined>
    const Probe = defineComponent({
      setup() {
        data = useQuery({ key: () => ['k', id.value], query }).data as Ref<string | undefined>
        return () => h('div')
      },
    })
    const wrapper = await mountSuspended(Probe)
    await flush()
    expect(data.value).toBe('data-a')
    id.value = 'b'
    await flush()
    expect(data.value).toBe('data-a')
    resolveB('data-b')
    await flush()
    expect(data.value).toBe('data-b')
    wrapper.unmount()
  })
})
