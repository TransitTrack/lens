import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { server } from '../../mocks/server'
import VehicleMap from '../../app/components/vehicles/VehicleMap.vue'
import { fixtures } from '../../mocks/handlers'
import { withFeedContext } from '../helpers/feedContext'

// @indoorequal/vue-maplibre-gl is aliased to tests/mocks/vue-maplibre-gl.ts:
// MglSymbolLayer renders a `.mgl-symbol-feature-stub` probe per GeoJSON feature
// and re-emits `click` with the clicked feature, the shape VehicleMap.vue's
// vehicle icon layer expects.

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('VehicleMap', () => {
  it('renders one icon per vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles }, ...withFeedContext() })
    expect(wrapper.findAll('.mgl-symbol-feature-stub')).toHaveLength(fixtures.vehicles.length)
  })

  it('opens the in-place detail card for the clicked vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles }, ...withFeedContext() })
    expect(wrapper.find('[data-testid="vehicle-card"]').exists()).toBe(false)
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    expect(wrapper.get('[data-testid="vehicle-card"]').text()).toContain(fixtures.vehicles[0]!.label)
  })

  it('closes the card from its close button and collapses it to a bar', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles }, ...withFeedContext() })
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    const card = () => wrapper.get('[data-testid="vehicle-card"]')
    await card().get('button[aria-expanded="true"]').trigger('click')
    expect(card().get('button[aria-expanded="false"]').exists()).toBe(true)
    await card().findAll('button').at(1)!.trigger('click')
    expect(wrapper.find('[data-testid="vehicle-card"]').exists()).toBe(false)
  })

  it('shows the popup vehicle\'s detail, read by the page, in its card', async () => {
    const vehicle = fixtures.vehicles[0]!
    const detail = {
      vehicle: { ...vehicle, currentStop: { stopId: 'S1', stopName: 'Main Square', stopLat: null, stopLon: null }, trip: null, block: null, pattern: null },
      vehiclePredictions: [],
    }
    const wrapper = await mountSuspended(VehicleMap, {
      props: { vehicles: fixtures.vehicles, cardDetail: detail as never },
      ...withFeedContext(),
    })
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    expect(wrapper.get('[data-testid="vehicle-card"]').text()).toContain('Main Square')
  })
})
