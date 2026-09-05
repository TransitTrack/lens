export function useDashboardSelection() {
  const selectedFeedCode = useState<string | null>('selectedFeedCode', () => null)
  const selectedVehicleId = useState<string | null>('selectedVehicleId', () => null)

  function selectFeed(code: string | null) {
    selectedFeedCode.value = code
    selectedVehicleId.value = null
  }

  function selectVehicle(id: string | null) {
    selectedVehicleId.value = id
  }

  return { selectedFeedCode, selectedVehicleId, selectFeed, selectVehicle }
}
