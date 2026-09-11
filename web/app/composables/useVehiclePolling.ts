import { useVehiclesQuery, type VehiclesQuery } from '../../generated/graphql'
import { usePollControl } from './usePollControl'

export type VehicleRow = VehiclesQuery['vehicles'][number]

export function useVehiclePolling(feedCode: Ref<string | null>) {
  const { intervalMs } = usePollControl()

  const { result, loading, error } = useVehiclesQuery(
    () => ({ feedCode: feedCode.value ?? '', matchedOnly: false }),
    () => ({
      enabled: !!feedCode.value,
      pollInterval: intervalMs.value,
      // This list is wholly replaced every poll (positions on ~150 vehicles,
      // each with nested trip/route/block). Writing it through Apollo's
      // InMemoryCache means normalizing + diffing all of that on every tick —
      // that's the actual main-thread stall, not the map rendering. Nothing
      // reads these entities back out of the cache, so skip it entirely.
      fetchPolicy: 'no-cache',
    }),
  )

  const vehicles = computed<VehicleRow[]>(() => result.value?.vehicles ?? [])

  return { vehicles, loading, error }
}
