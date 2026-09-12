import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import VehicleList from '../../app/components/VehicleList.vue'
import { fixtures } from '../../mocks/handlers'

describe('VehicleList', () => {
  it('renders one operational row per vehicle', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    expect(wrapper.text()).toContain('bus-1')
    expect(wrapper.text()).toContain('bus-2')
    expect(wrapper.find('[data-testid="vehicle-row-bus-1"]').exists()).toBe(true)
  })

  it('clicking a row selects that vehicle and emits select', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    await wrapper.find('[data-testid="vehicle-row-bus-1"]').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual(['bus-1'])
  })

  it('clicking the Vehicle column header reverses row order', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    const rowsBefore = wrapper.findAll('[data-testid^="vehicle-row-"]')
    expect(rowsBefore[0]!.text()).toContain('bus-1')

    await wrapper.find('[data-testid="vehicle-sort"]').trigger('click')

    const rowsAfter = wrapper.findAll('[data-testid^="vehicle-row-"]')
    expect(rowsAfter[0]!.text()).toContain('bus-2')
  })
})
