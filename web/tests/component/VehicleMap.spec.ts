import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import VehicleMap from '../../app/components/VehicleMap.vue'
import { fixtures } from '../../mocks/handlers'

// @indoorequal/vue-maplibre-gl is aliased to tests/mocks/vue-maplibre-gl.ts:
// MglMarker renders a `.mgl-marker-stub` probe per marker.

describe('VehicleMap', () => {
  it('renders one marker per vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    expect(wrapper.findAll('.mgl-marker-stub')).toHaveLength(fixtures.vehicles.length)
  })

  it('exposes flyTo', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    const vm = wrapper.vm as unknown as { flyTo: (vehicleId: string) => void }
    expect(typeof vm.flyTo).toBe('function')
    expect(() => vm.flyTo('bus-1')).not.toThrow()
  })
})
