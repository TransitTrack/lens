import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { computed, h, ref } from 'vue'
import { UApp } from '#components'
import HeadwayPage from '../../app/pages/[feedCode]/headway.vue'
import { usePollControl } from '../../app/composables/usePollControl'
import { withFeedContext } from '../helpers/feedContext'
import { json, page, stubFetch } from '../helpers/stubFetch'

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

describe('headway page polling', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  async function mountPage() {
    const { calls } = stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/headway')) return json({ gapsSec: [] })
      return page([])
    })
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(HeadwayPage)) },
      withFeedContext({ selectedFeedCode: ref('a'), selectedAvlFeedCode: computed(() => 'avl-a') }),
    )
    await settle()
    const vm = wrapper.findComponent(HeadwayPage).vm as unknown as {
      selectedRouteId: string | null
      selectedStopId: string | null
      selectedDirectionId: number | null
    }
    const count = (suffix: string) => calls.filter((c) => new URL(c.url).pathname.endsWith(suffix)).length
    return { wrapper, vm, count }
  }

  async function pick(vm: { selectedRouteId: string | null, selectedStopId: string | null }, stop: string) {
    vm.selectedRouteId = 'r1'
    await settle()
    vm.selectedStopId = stop
    await settle()
  }

  it('polls while running', async () => {
    const { wrapper, vm, count } = await mountPage()
    await pick(vm, 's1')
    expect(count('/headway')).toBe(1)
    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 3 + 100)
    await settle()
    expect(count('/headway')).toBeGreaterThan(1)
    expect(count('/predictions')).toBeGreaterThan(1)
    wrapper.unmount()
  })

  it('loads once when paused with no data, and again for a changed stop, but never polls', async () => {
    const { wrapper, vm, count } = await mountPage()
    usePollControl().paused.value = true
    await settle()
    await pick(vm, 's1')
    expect(count('/headway')).toBe(1)
    expect(count('/predictions')).toBe(1)

    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 5 + 100)
    await settle()
    expect(count('/headway')).toBe(1)
    expect(count('/predictions')).toBe(1)

    vm.selectedStopId = 's2'
    await settle()
    expect(count('/headway')).toBe(2)
    expect(count('/predictions')).toBe(2)
    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 5 + 100)
    await settle()
    expect(count('/headway')).toBe(2)
    wrapper.unmount()
  })

  it('stops polling once paused with data', async () => {
    const { wrapper, vm, count } = await mountPage()
    await pick(vm, 's1')
    usePollControl().paused.value = true
    await settle()
    const before = count('/headway')
    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 5 + 100)
    await settle()
    // The plugin's already scheduled tick may fire once after the pause.
    expect(count('/headway')).toBeLessThanOrEqual(before + 1)
    const settled = count('/headway')
    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 5 + 100)
    expect(count('/headway')).toBe(settled)
    wrapper.unmount()
  })
})
