import { describe, expect, it } from 'vitest'
import { useDashboardSelection } from '../../app/composables/useDashboardSelection'

describe('useDashboardSelection', () => {
  it('selecting a feed clears the selected vehicle', () => {
    const { selectedFeedCode, selectedVehicleId, selectFeed, selectVehicle } =
      useDashboardSelection()
    selectFeed('feed-a')
    selectVehicle('bus-1')
    expect(selectedVehicleId.value).toBe('bus-1')
    selectFeed('feed-b')
    expect(selectedFeedCode.value).toBe('feed-b')
    expect(selectedVehicleId.value).toBeNull()
  })

  it('selectVehicle sets and clears independently of feed', () => {
    const { selectedVehicleId, selectVehicle } = useDashboardSelection()
    selectVehicle('bus-2')
    expect(selectedVehicleId.value).toBe('bus-2')
    selectVehicle(null)
    expect(selectedVehicleId.value).toBeNull()
  })
})
