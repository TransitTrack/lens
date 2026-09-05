import { describe, expect, it, beforeAll, afterAll, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { server } from '../../mocks/server'
import VehiclePredictionPanel from '../../app/components/VehiclePredictionPanel.vue'
import { useDashboardSelection } from '../../app/composables/useDashboardSelection'

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('VehiclePredictionPanel', () => {
  it('shows predictions once a vehicle is selected', async () => {
    const { selectFeed, selectVehicle } = useDashboardSelection()
    selectFeed('feed-a')
    selectVehicle('bus-1')
    const wrapper = await mountSuspended(VehiclePredictionPanel)
    await new Promise((r) => setTimeout(r, 0))
    expect(wrapper.text()).toContain('Main St')
    expect(wrapper.text()).toContain('SCHEDULE_ADHERENCE')
  })

  it('closing clears the selected vehicle', async () => {
    const { selectFeed, selectVehicle, selectedVehicleId } = useDashboardSelection()
    selectFeed('feed-a')
    selectVehicle('bus-1')
    await mountSuspended(VehiclePredictionPanel)
    await new Promise((r) => setTimeout(r, 0))
    selectVehicle(null)
    expect(selectedVehicleId.value).toBeNull()
  })
})
