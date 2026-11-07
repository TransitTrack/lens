import { usePollControl } from './usePollControl'
import { fetchAllVehicles } from '~/utils/paginatedVehicles'

export interface VehicleRow {
  vehicleId: string
  label: string | null
  reportTs: string
  position: { lat: number, lon: number }
  bearing: number | null
  matched: boolean
  stale: boolean
  scheduleAdherenceSec: number | null
  stopPathIndex: number | null
  distanceAlongTripM: number | null
  speedMps: number | null
  pattern: { lengthM: number | null } | null
  trip: { tripId: string, routeId: string, tripHeadsign: string | null, route: {
    routeShortName: string | null, routeColor: string | null, routeTextColor: string | null
  } | null } | null
  block: { blockId: string, serviceId: string } | null
}

export function useVehiclePolling(feedCode: Ref<string | null>) {
  const { intervalMs } = usePollControl()
  const vehicles = shallowRef<VehicleRow[]>([])
  const loading = ref(false)
  const error = shallowRef<Error | null>(null)

  watch([feedCode, intervalMs], ([feed, interval], previous, onCleanup) => {
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    onCleanup(() => { controller.abort(); if (timer) clearTimeout(timer) })
    if (!previous || previous[0] !== feed) vehicles.value = []
    error.value = null
    if (!feed) { loading.value = false; return }
    const refresh = async () => {
      loading.value = true
      try {
        const response = await fetchAllVehicles(feed, controller.signal)
        if (!controller.signal.aborted) { vehicles.value = response; error.value = null }
      } catch (cause) {
        if (!controller.signal.aborted) error.value = cause instanceof Error ? cause : new Error(String(cause))
      } finally {
        if (!controller.signal.aborted) {
          loading.value = false
          if (interval > 0) timer = setTimeout(refresh, interval)
        }
      }
    }
    void refresh()
  }, { immediate: true })

  return { vehicles, loading, error }
}
