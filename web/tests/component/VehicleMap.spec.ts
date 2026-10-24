import { describe, expect, it, beforeAll, afterAll, afterEach, vi } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { graphql, HttpResponse } from 'msw'
import { server } from '../../mocks/server'
import VehicleMap from '../../app/components/VehicleMap.vue'
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

  it('opens the popup for the clicked vehicle', async () => {
    const wrapper = await mountSuspended(VehicleMap, { props: { vehicles: fixtures.vehicles } })
    await wrapper.get('.mgl-symbol-feature-stub').trigger('click')
    expect(wrapper.find('.mgl-popup-stub').text()).toContain(fixtures.vehicles[0]!.label)
  })

  it('keeps polling the active vehicle while its popup stays open', async () => {
    let vehicleDetailRequests = 0
    server.use(
      graphql.query('VehicleDetail', () => {
        vehicleDetailRequests += 1
        return HttpResponse.json({ data: { vehicle: null } })
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
