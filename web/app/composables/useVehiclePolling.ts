import { useVehiclesQuery, type VehiclesQuery } from '../../generated/graphql'

export type VehicleRow = VehiclesQuery['vehicles'][number]

export function useVehiclePolling(feedCode: Ref<string | null>) {
  const { result, loading, error } = useVehiclesQuery(
    () => ({ feedCode: feedCode.value ?? '', matchedOnly: false }),
    () => ({ enabled: !!feedCode.value, pollInterval: 5000 }),
  )

  const vehicles = computed<VehicleRow[]>(() => result.value?.vehicles ?? [])

  return { vehicles, loading, error }
}
