import { describe, expect, it, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { server } from '../../mocks/server'
import IndexPage from '../../app/pages/index.vue'

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
  it('renders the feed picker, map, and vehicle list together', async () => {
    const wrapper = await mountSuspended(IndexPage)
    await new Promise((r) => setTimeout(r, 0))
    expect(wrapper.text()).toContain('bus-1')
    expect(wrapper.text()).toContain('bus-2')
  })
})
