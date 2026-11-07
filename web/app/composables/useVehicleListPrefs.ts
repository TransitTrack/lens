import { createSharedComposable, useLocalStorage } from '@vueuse/core'

/**
 * Vehicle list display options, shared by every vehicle list (Vehicles page, Overview) and
 * remembered per browser in localStorage.
 */
const _useVehicleListPrefs = () => {
  /** the "→ headsign · trip … · block …" line under each matched vehicle */
  const showTripDetails = useLocalStorage('tt-vehicle-list-trip-details', true)
  /** the Overview's floating vehicle list folded away to a button, leaving the map clear */
  const boardListCollapsed = useLocalStorage('tt-operations-list-collapsed', false)
  return { showTripDetails, boardListCollapsed }
}

export const useVehicleListPrefs = createSharedComposable(_useVehicleListPrefs)
