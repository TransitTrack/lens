import { describe, expect, it, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { server } from '../../mocks/server'
import IndexPage from '../../app/pages/index.vue'
import { useDashboardSelection } from '../../app/composables/useDashboardSelection'

vi.mock('maplibre-gl', () => {
  class FakeMarker {
    element: HTMLElement
    constructor(opts: { element: HTMLElement }) {
      this.element = opts.element
    }
    setLngLat() {
      return this
    }
    addTo() {
      return this
    }
    remove() {
      return this
    }
    getElement() {
      return this.element
    }
  }
  class FakeMap {
    flyTo() {}
    remove() {}
  }
  return { Map: FakeMap, Marker: FakeMarker }
})

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('dashboard page', () => {
  it('renders the map and vehicle list together for the selected feed', async () => {
    // FeedPicker (which normally auto-selects the first feed) now lives in the
    // shared layout, not in IndexPage itself — select a feed directly so this
    // page-only mount has something to poll.
    useDashboardSelection().selectFeed('feed-a')
    const wrapper = await mountSuspended(IndexPage)
    await new Promise((r) => setTimeout(r, 0))
    expect(wrapper.text()).toContain('bus-1')
    expect(wrapper.text()).toContain('bus-2')
  })
})
