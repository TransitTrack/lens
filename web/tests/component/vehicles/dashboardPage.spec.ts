import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { computed, defineComponent, h, ref } from 'vue'
import { UApp } from '#components'
import DashboardPage from '../../../app/pages/[feedCode]/index.vue'
import { usePollControl } from '../../../app/composables/usePollControl'
import type { AvlFeed } from '../../../app/api'
import { withFeedContext } from '../../helpers/feedContext'
import { json, page, stubFetch } from '../../helpers/stubFetch'

// The board (and the map inside it) is replaced by a probe: these tests check the page's reads and what it
// hands the board, without VehicleMap's canvas drawing.
const BoardProbe = vi.hoisted(() => ({ props: {} as Record<string, unknown> }))
vi.mock('../../../app/components/vehicles/VehicleOperationsBoard.vue', () => ({
  default: defineComponent({
    name: 'VehicleOperationsBoard',
    props: ['title', 'description', 'vehicles', 'loading', 'error', 'feedCode', 'extent', 'networkFeatures',
      'detail', 'cardDetail', 'cardDetailLoading', 'recentReports', 'agencyTimezone', 'selectedVehicleId', 'activeVehicleId'],
    emits: ['update:selectedVehicleId', 'update:activeVehicleId', 'selectVehicle', 'networkArmed'],
    setup(props) {
      BoardProbe.props = props
      return () => h('div', { 'data-testid': 'board-probe' })
    },
  }),
}))

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

const vehicleOf = (vehicleId: string) => ({
  vehicleId, label: vehicleId, reportTs: '2026-11-09T10:00:00Z', position: { lat: 1, lon: 2 }, matched: false, stale: false,
})

describe('dashboard page vehicle reads', () => {
  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
    usePollControl().paused.value = false
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('reads the card\'s detail for the popup vehicle and the overlay\'s for the selected one, reports only with a popup', async () => {
    const { calls } = stubFetch(({ url }) => {
      const { pathname } = new URL(url)
      if (pathname.endsWith('/feeds/avl/vehicles')) return json([vehicleOf('a1'), vehicleOf('b2')])
      const vehicle = pathname.match(/\/avl\/vehicles\/(\w+)$/)
      if (vehicle) return json(vehicleOf(vehicle[1]!))
      if (pathname.endsWith('/reports')) return json([])
      return page([])
    })
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(DashboardPage)) },
      withFeedContext({
        selectedFeedCode: ref('g'),
        selectedAvlFeedCode: computed(() => 'avl'),
        avlFeeds: ref([{ code: 'avl', gtfsFeedCode: 'g' } as AvlFeed]),
      }),
    )
    await settle()
    const board = wrapper.findComponent({ name: 'VehicleOperationsBoard' })
    const count = (suffix: string) => calls.filter((c) => new URL(c.url).pathname.endsWith(suffix)).length
    const vehicleIdOf = (detail: unknown) => (detail as { vehicle?: { vehicleId: string } } | null)?.vehicle?.vehicleId ?? null
    expect(count('/reports')).toBe(0)
    expect(count('/avl/vehicles/a1')).toBe(0)

    // A click on the map opens the popup and selects the vehicle.
    board.vm.$emit('update:activeVehicleId', 'a1')
    board.vm.$emit('update:selectedVehicleId', 'a1')
    await settle()
    expect(vehicleIdOf(BoardProbe.props.cardDetail)).toBe('a1')
    expect(vehicleIdOf(BoardProbe.props.detail)).toBe('a1')
    expect(count('/avl/vehicles/a1')).toBe(1) // one cache entry for both reads
    expect(count('/avl/vehicles/a1/reports')).toBe(1)

    // The list then selects another vehicle while the popup stays on the first.
    board.vm.$emit('update:selectedVehicleId', 'b2')
    await settle()
    expect(vehicleIdOf(BoardProbe.props.detail)).toBe('b2')
    expect(vehicleIdOf(BoardProbe.props.cardDetail)).toBe('a1')

    // Closing the popup stops the report reads.
    board.vm.$emit('update:activeVehicleId', null)
    await settle()
    expect(BoardProbe.props.recentReports).toEqual([])
    const reports = count('/reports')
    await vi.advanceTimersByTimeAsync(usePollControl().intervalMs.value * 2 + 100)
    await settle()
    expect(count('/reports')).toBe(reports)
    expect(count('/avl/vehicles/b2')).toBeGreaterThan(1) // the selected vehicle keeps polling
    wrapper.unmount()
  })
})
