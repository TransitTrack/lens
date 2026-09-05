import { describe, expect, it, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import VehicleMap from '../../app/components/VehicleMap.vue'
import { fixtures } from '../../mocks/handlers'

const markerInstances: any[] = []

vi.mock('maplibre-gl', () => {
  class FakeMarker {
    element: HTMLElement
    lngLat: [number, number] = [0, 0]
    constructor(opts: { element: HTMLElement }) {
      this.element = opts.element
      markerInstances.push(this)
    }
    setLngLat(ll: [number, number]) {
      this.lngLat = ll
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
    flyToCalls: unknown[] = []
    flyTo(opts: unknown) {
      this.flyToCalls.push(opts)
    }
    remove() {}
  }
  return { default: { Map: FakeMap, Marker: FakeMarker } }
})

describe('VehicleMap', () => {
  it('creates one marker per vehicle', async () => {
    markerInstances.length = 0
    await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    expect(markerInstances).toHaveLength(fixtures.vehicles.length)
  })

  it('exposes flyTo', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    expect(typeof (wrapper.vm as any).flyTo).toBe('function')
    expect(() => (wrapper.vm as any).flyTo('bus-1')).not.toThrow()
  })
})
