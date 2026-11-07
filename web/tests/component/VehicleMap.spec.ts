import { describe, expect, it, beforeAll, afterAll, afterEach, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { http, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import VehicleMap from '../../app/components/vehicles/VehicleMap.vue'
import { fixtures } from '../../mocks/handlers'

// @indoorequal/vue-maplibre-gl is aliased to tests/mocks/vue-maplibre-gl.ts:
// MglSymbolLayer renders a `.mgl-symbol-feature-stub` probe per GeoJSON feature
// and re-emits `click` with the clicked feature, the shape VehicleMap.vue's
// vehicle icon layer expects.

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('VehicleMap', () => {
  it('renders one icon per vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    expect(wrapper.findAll('.mgl-symbol-feature-stub')).toHaveLength(fixtures.vehicles.length)
  })

  it('opens the in-place detail card for the clicked vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    expect(wrapper.find('[data-testid="vehicle-card"]').exists()).toBe(false)
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    expect(wrapper.get('[data-testid="vehicle-card"]').text()).toContain(fixtures.vehicles[0]!.label)
  })

  it('closes the card from its close button and collapses it to a bar', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    const card = () => wrapper.get('[data-testid="vehicle-card"]')
    await card().get('button[aria-expanded="true"]').trigger('click')
    expect(card().get('button[aria-expanded="false"]').exists()).toBe(true)
    await card().findAll('button').at(1)!.trigger('click')
    expect(wrapper.find('[data-testid="vehicle-card"]').exists()).toBe(false)
  })

  it('keeps polling the active vehicle while its popup stays open', async () => {
    let vehicleDetailRequests = 0
    server.use(
      http.get('*/api/v1/avl-feeds/:feedCode/vehicles/:vehicleId', () => {
        vehicleDetailRequests += 1
        return new HttpResponse(null, { status: 404 })
      }),
    )

    const wrapper = await mountSuspended(VehicleMap, {
      props: { vehicles: fixtures.vehicles, feedCode: 'feed-a' },
    })
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')

    await vi.waitFor(
      () => {
        expect(vehicleDetailRequests).toBeGreaterThanOrEqual(2)
      },
      { timeout: 12_000 },
    )
  }, 15_000)
})
