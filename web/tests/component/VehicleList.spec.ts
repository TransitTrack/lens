import { describe, expect, it } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import VehicleList from '../../app/components/VehicleList.vue'
import { fixtures } from '../../mocks/handlers'
import { useDashboardSelection } from '../../app/composables/useDashboardSelection'

describe('VehicleList', () => {
  it('renders one row per vehicle with a status badge', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    expect(wrapper.text()).toContain('bus-1')
    expect(wrapper.text()).toContain('bus-2')
  })

  it('clicking a row selects that vehicle and emits select', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    const rows = wrapper.findAll('tbody tr')
    await rows[0]!.trigger('click')
    const { selectedVehicleId } = useDashboardSelection()
    expect(selectedVehicleId.value).toBe('bus-1')
    expect(wrapper.emitted('select')?.[0]).toEqual(['bus-1'])
  })

  it('clicking the Vehicle column header reverses row order', async () => {
    const wrapper = await mountSuspended(VehicleList, { props: { vehicles: fixtures.vehicles } })
    const rowsBefore = wrapper.findAll('tbody tr')
    expect(rowsBefore[0]!.text()).toContain('bus-1')

    const header = wrapper.findAll('thead button').find((b) => b.text() === 'Vehicle')
    await header!.trigger('click')

    const rowsAfter = wrapper.findAll('tbody tr')
    expect(rowsAfter[0]!.text()).toContain('bus-2')
  })
})
