import { useVehiclesQuery, type VehiclesQuery } from '../../generated/graphql'
import { usePollControl } from './usePollControl'

export type VehicleRow = VehiclesQuery['vehicles'][number]

export function useVehiclePolling(feedCode: Ref<string | null>) {
  const { intervalMs } = usePollControl()

  const { result, loading, error } = useVehiclesQuery(
    () => ({ feedCode: feedCode.value ?? '', matchedOnly: false }),
    () => ({ enabled: !!feedCode.value, pollInterval: intervalMs.value }),
  )

  const vehicles = computed<VehicleRow[]>(() => result.value?.vehicles ?? [])

  return { vehicles, loading, error }
}
