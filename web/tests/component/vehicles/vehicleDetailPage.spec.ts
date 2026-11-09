import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import { computed, h, ref } from 'vue'
import { UApp } from '#components'
import VehiclePage from '../../../app/pages/[feedCode]/vehicles/[vehicleId].vue'
import { usePollControl } from '../../../app/composables/usePollControl'
import type { AvlFeed } from '../../../app/api'
import { withFeedContext } from '../../helpers/feedContext'
import { json, page, stubFetch } from '../../helpers/stubFetch'

mockNuxtImport('useRoute', () => () => ({ params: { vehicleId: 'v1' }, query: {} }))

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

const vehicle = {
  vehicleId: 'v1', label: 'Bus 1', reportTs: '2026-11-09T10:00:00Z', position: { lat: 1, lon: 2 },
  matched: true, stale: false, revisionId: '51', tripId: 'T', currentStopId: 'S1',
}

const report = {
  vehicleId: 'v1', ts: '2026-11-09T10:00:00Z', processedAt: '2026-11-09T10:00:01Z', source: 'GTFS_RT',
  position: { lat: 1, lon: 2 }, matchStatus: 'MATCHED',
  match: { revisionId: '51', tripId: 'T', stopPathIndex: 0, distanceAlongTripM: 0, deviationM: 1, snappedPosition: { lat: 1, lon: 2 } },
}

describe('vehicle detail page polling', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
    usePollControl().paused.value = false
  })
  afterEach(() => {
    usePollControl().paused.value = false
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  async function mountPage() {
    const { calls } = stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/avl/vehicles/v1')) return json(vehicle)
      if (pathname.endsWith('/avl/vehicles/v1/trail')) return json({ track: [], speeds: [] })
      if (pathname.endsWith('/trips/T')) return json({ tripId: 'T', routeId: 'R', serviceId: 'WK', patternKey: 'P' })
      if (pathname.endsWith('/routes/R')) return json({ routeId: 'R', routeShortName: '7' })
      if (pathname.endsWith('/trip-patterns/P')) return json({ patternKey: 'P', geometry: null })
      if (pathname.endsWith('/avl/vehicles/v1/reports')) return json([report])
      if (pathname.endsWith('/feeds/g/routes')) return page([{ routeId: 'R', routeShortName: 'R-SEVEN' }])
      return page([])
    })
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(VehiclePage)) },
      withFeedContext({
        selectedFeedCode: ref('g'),
        selectedAvlFeedCode: computed(() => 'avl'),
        avlFeeds: ref([{ code: 'avl', gtfsFeedCode: 'g' } as AvlFeed]),
      }),
    )
    await settle()
    const vm = wrapper.findComponent(VehiclePage).vm as unknown as { avlLogOpen: boolean }
    const paths = () => calls.map((c) => new URL(c.url).pathname)
    const count = (suffix: string) => paths().filter((p) => p.endsWith(suffix)).length
    return { wrapper, vm, calls, paths, count }
  }

  it('polls the vehicle but reads its revision-pinned trip once, and pausing stops every request', async () => {
    const { wrapper, calls, paths, count } = await mountPage()
    const interval = usePollControl().intervalMs.value
    expect(interval).toBe(5000)
    expect(count('/avl/vehicles/v1')).toBe(1)

    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    expect(count('/avl/vehicles/v1')).toBe(3)
    expect(count('/avl/vehicles/v1/predictions')).toBe(3)
    expect(paths().filter((p) => p.includes('/trips/'))).toHaveLength(1)
    expect(count('/routes/R')).toBe(1)
    expect(count('/trip-patterns/P')).toBe(1)
    // The current stop is not on the (empty) pattern: read by id once, not per poll.
    expect(paths().filter((p) => p.endsWith('/feeds/g/stops'))).toHaveLength(1)
    expect(wrapper.text()).toContain('7')

    usePollControl().toggle()
    await settle()
    const before = calls.length
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    expect(calls.length).toBe(before)
    wrapper.unmount()
  })

  it('reads the AVL log with its trips and routes only while the log is open', async () => {
    const { wrapper, vm, calls, count } = await mountPage()
    const interval = usePollControl().intervalMs.value
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    expect(count('/avl/vehicles/v1/reports')).toBe(0)

    vm.avlLogOpen = true
    await settle()
    expect(count('/avl/vehicles/v1/reports')).toBe(1)
    const routeReads = calls.filter((c) => new URL(c.url).pathname.endsWith('/feeds/g/routes')).map((c) => new URL(c.url).searchParams)
    expect(routeReads.map((q) => `${q.get('revisionId')}:${q.getAll('routeId').join(',')}`)).toEqual(['51:R'])
    await vi.waitFor(() => expect(document.body.textContent).toContain('R-SEVEN'))

    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    expect(count('/avl/vehicles/v1/reports')).toBe(2)

    vm.avlLogOpen = false
    await settle()
    const closed = count('/avl/vehicles/v1/reports')
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    await vi.advanceTimersByTimeAsync(interval)
    await settle()
    expect(count('/avl/vehicles/v1/reports')).toBe(closed)
    wrapper.unmount()
  })
})
