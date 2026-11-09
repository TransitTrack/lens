<script setup lang="ts">
import { useQuery, useQueryCache, type EntryKey } from '@pinia/colada'
import { liveVehiclesQuery } from '~/composables/api/queries/avl/liveVehiclesQuery'
import { vehicleQuery } from '~/composables/api/queries/avl/vehicleQuery'
import { vehiclePredictionsQuery } from '~/composables/api/queries/avl/vehiclePredictionsQuery'
import { avlReportsQuery } from '~/composables/api/queries/avl/avlReportsQuery'
import { agenciesQuery } from '~/composables/api/queries/gtfs/agenciesQuery'
import { tripQuery } from '~/composables/api/queries/gtfs/tripQuery'
import { routeQuery } from '~/composables/api/queries/gtfs/routeQuery'
import { routeStopsQuery } from '~/composables/api/queries/gtfs/routeStopsQuery'
import { shapeQuery } from '~/composables/api/queries/gtfs/shapeQuery'
import { stopsByIdsQuery } from '~/composables/api/queries/gtfs/stopsByIdsQuery'
import { feedGeometryQuery } from '~/composables/api/queries/schedule/feedGeometryQuery'
import { tripPatternQuery } from '~/composables/api/queries/schedule/tripPatternQuery'
import { stopPathsQuery } from '~/composables/api/queries/schedule/stopPathsQuery'
import { blockQuery } from '~/composables/api/queries/schedule/blockQuery'
import { blockTripsQuery } from '~/composables/api/queries/schedule/blockTripsQuery'
import type { PredictionAlgorithm } from '~/api'
import { agenciesExtent } from '~/utils/joins/extent'
import { gtfsFeedCodeOf } from '~/utils/joins/feeds'
import { missingStopIds, needsShape, vehicleDetail, vehiclePattern, type VehicleDetail } from '~/utils/joins/vehicleDetail'
import { chosenAlgorithm } from '~/utils/vehicleDetail'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
import VehicleOperationsBoard from '~/components/vehicles/VehicleOperationsBoard.vue'
import { useFeeds } from '~/composables/useFeeds'
import { usePollControl } from '~/composables/usePollControl'

const { t } = useI18n()
const { selectedFeedCode, selectedFeed, selectedAvlFeedCode, feedPath, avlFeeds } = useFeeds()
const { paused, intervalMs } = usePollControl()
const queryCache = useQueryCache()

// Polled queries: pausing stops auto-refetch; a paused query still loads once when its own key has no
// cached data yet, then stays idle. The cache is read by key, not through `data` (the previous key's placeholder).
const pollInterval = () => (paused.value ? false : intervalMs.value)
const pollEnabled = (base: boolean, key: EntryKey) =>
  base && (!paused.value || queryCache.getQueryData(key) === undefined)

const { data: vehiclesData, isLoading: loading, error } = useQuery(() => {
  const options = liveVehiclesQuery({ feedCode: selectedAvlFeedCode.value ?? '' })
  return { ...options, enabled: pollEnabled(!!selectedAvlFeedCode.value, options.key), autoRefetch: pollInterval }
})
const vehicles = computed(() => vehiclesData.value ?? [])

const { data: agencies } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: !!selectedFeedCode.value,
}))
const extent = computed(() => agenciesExtent(agencies.value ?? []))
const agencyTimezone = computed(() => agencies.value?.find((agency) => agency.agencyTimezone)?.agencyTimezone ?? null)

// The network underlay is read only once the map has loaded and the browser is idle (`networkArmed`).
const networkArmed = ref(false)
const { data: geometry } = useQuery(() => ({
  ...feedGeometryQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: networkArmed.value && !!selectedFeedCode.value,
}))
const networkFeatures = computed(() => (geometry.value as GeoJSON.FeatureCollection | undefined)?.features ?? [])

// The vehicle picked on the board (map or list): its detail feeds the route overlay and the popup card.
const selectedVehicleId = ref<string | null>(null)
watch(selectedAvlFeedCode, () => {
  selectedVehicleId.value = null
})
const selecting = computed(() => !!selectedAvlFeedCode.value && !!selectedVehicleId.value)
const algorithm = computed(() => chosenAlgorithm(selectedFeed.value?.predictionAlgorithms ?? []))

// --- the selected vehicle and its relations ---------------------------------------
// The vehicle and its predictions poll; what they point into is pinned to the vehicle's revision and read once.

const vehicleQ = useQuery(() => {
  const options = vehicleQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: selectedVehicleId.value ?? '' })
  return { ...options, enabled: pollEnabled(selecting.value, options.key), autoRefetch: pollInterval }
})
const predictionsQ = useQuery(() => {
  const options = vehiclePredictionsQuery({
    avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: selectedVehicleId.value ?? '',
    algorithm: algorithm.value as PredictionAlgorithm | undefined,
  })
  return { ...options, enabled: pollEnabled(selecting.value, options.key), autoRefetch: pollInterval }
})
const liveVehicle = computed(() => (selecting.value ? vehicleQ.data.value ?? null : null))
const livePredictions = computed(() => (selecting.value ? predictionsQ.data.value ?? [] : []))

const gtfsFeedCode = computed(() =>
  selectedAvlFeedCode.value ? gtfsFeedCodeOf(avlFeeds.value, selectedAvlFeedCode.value) : null,
)
const scope = computed(() => {
  const revisionId = liveVehicle.value?.revisionId
  return gtfsFeedCode.value && revisionId ? { feedCode: gtfsFeedCode.value, revisionId } : null
})
const pinned = () => scope.value ?? { feedCode: '' }

const tripEnabled = computed(() => !!scope.value && !!liveVehicle.value?.tripId)
const tripQ = useQuery(() => ({
  ...tripQuery({ ...pinned(), tripId: liveVehicle.value?.tripId ?? '' }),
  enabled: tripEnabled.value,
}))
const trip = computed(() => (tripEnabled.value ? tripQ.data.value ?? null : null))

const routeEnabled = computed(() => !!trip.value)
const routeQ = useQuery(() => ({
  ...routeQuery({ ...pinned(), routeId: trip.value?.routeId ?? '' }),
  enabled: routeEnabled.value,
}))
const patternEnabled = computed(() => !!trip.value?.patternKey)
const patternQ = useQuery(() => ({
  ...tripPatternQuery({ ...pinned(), patternKey: trip.value?.patternKey ?? '' }),
  enabled: patternEnabled.value,
}))
const stopPathsQ = useQuery(() => ({
  ...stopPathsQuery({ ...pinned(), routeId: trip.value?.routeId, patternKey: trip.value?.patternKey ?? undefined }),
  enabled: patternEnabled.value,
}))
const routeStopsQ = useQuery(() => ({
  ...routeStopsQuery({ ...pinned(), routeId: trip.value?.routeId ?? '' }),
  enabled: patternEnabled.value,
}))
const blockEnabled = computed(() => !!trip.value?.blockId)
const blockParams = () => ({ ...pinned(), blockId: trip.value?.blockId ?? '', serviceId: trip.value?.serviceId ?? '' })
const blockQ = useQuery(() => ({ ...blockQuery(blockParams()), enabled: blockEnabled.value }))
const blockTripsQ = useQuery(() => ({ ...blockTripsQuery(blockParams()), enabled: blockEnabled.value }))

/** Still waiting on an enabled read (no data of its own key yet, and no error). */
const pending = (enabled: boolean, q: { data: Readonly<Ref<unknown>>, error: Readonly<Ref<unknown>>, isPlaceholderData: Readonly<Ref<boolean>> }) =>
  enabled && !q.error.value && (q.data.value === undefined || q.isPlaceholderData.value)

const pattern = computed(() => (patternEnabled.value ? patternQ.data.value ?? null : null))
const patternSettled = computed(() =>
  !pending(tripEnabled.value, tripQ)
  && !pending(patternEnabled.value, patternQ) && !pending(patternEnabled.value, stopPathsQ) && !pending(patternEnabled.value, routeStopsQ),
)
const joinedPattern = computed(() =>
  trip.value ? vehiclePattern(trip.value, pattern.value, stopPathsQ.data.value ?? [], routeStopsQ.data.value ?? []) : null,
)
// The route line falls back to the GTFS shape only when the pattern has no usable geometry.
const shapeEnabled = computed(() => patternSettled.value && !!trip.value?.shapeId && needsShape(pattern.value))
const shapeQ = useQuery(() => ({
  ...shapeQuery({ ...pinned(), shapeId: trip.value?.shapeId ?? '' }),
  enabled: shapeEnabled.value,
}))
// The current and predicted stops sit on the pattern; read only those that aren't there.
const missingStops = computed(() =>
  patternSettled.value && !pending(selecting.value, vehicleQ) && !pending(selecting.value, predictionsQ)
    ? missingStopIds(liveVehicle.value, livePredictions.value, joinedPattern.value)
    : [],
)
const stopsEnabled = computed(() => !!scope.value && missingStops.value.length > 0)
const stopsQ = useQuery(() => ({
  ...stopsByIdsQuery({ ...pinned(), stopIds: missingStops.value }),
  enabled: stopsEnabled.value,
}))

const chain = computed(() => [
  { enabled: selecting.value, q: vehicleQ },
  { enabled: selecting.value, q: predictionsQ },
  { enabled: tripEnabled.value, q: tripQ },
  { enabled: routeEnabled.value, q: routeQ },
  { enabled: patternEnabled.value, q: patternQ },
  { enabled: patternEnabled.value, q: stopPathsQ },
  { enabled: patternEnabled.value, q: routeStopsQ },
  { enabled: blockEnabled.value, q: blockQ },
  { enabled: blockEnabled.value, q: blockTripsQ },
  { enabled: shapeEnabled.value, q: shapeQ },
  { enabled: stopsEnabled.value, q: stopsQ },
])
// Like the single composite read it replaces: the joined detail appears once every read it needs has
// answered, and the previous detail stays on screen meanwhile (a new trip, another vehicle).
const detail = computed<VehicleDetail | null>((previous) => {
  if (chain.value.some(({ enabled, q }) => pending(enabled, q))) return previous ?? null
  return vehicleDetail({
    vehicle: liveVehicle.value,
    predictions: livePredictions.value,
    trip: trip.value,
    route: routeEnabled.value ? routeQ.data.value ?? null : null,
    shape: shapeEnabled.value ? shapeQ.data.value ?? null : null,
    pattern: pattern.value,
    stopPaths: patternEnabled.value ? stopPathsQ.data.value ?? [] : [],
    routeStops: patternEnabled.value ? routeStopsQ.data.value ?? [] : [],
    block: blockEnabled.value ? blockQ.data.value ?? null : null,
    blockTrips: blockEnabled.value ? blockTripsQ.data.value ?? [] : [],
    stops: stopsEnabled.value ? stopsQ.data.value ?? [] : [],
  })
})
const detailLoading = computed(() => chain.value.some(({ enabled, q }) => enabled && q.isLoading.value))

// The newest two reports, for the card's speed estimate when the vehicle reports none.
const { data: reportsData } = useQuery(() => {
  const options = avlReportsQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: selectedVehicleId.value ?? '', limit: 2 })
  return { ...options, enabled: pollEnabled(selecting.value, options.key), autoRefetch: pollInterval }
})
const recentReports = computed(() => (selecting.value ? reportsData.value ?? [] : []))

const newestReport = computed(() =>
  vehicles.value.reduce<string | null>(
    (max, v) => (!max || v.reportTs > max ? v.reportTs : max),
    null,
  ),
)
</script>

<template>
  <AppPage full-width>
    <NoRealtimeState v-if="!selectedAvlFeedCode" :what="t('noRealtime.liveVehicleOperations')" />
    <VehicleOperationsBoard
      v-else
      v-model:selected-vehicle-id="selectedVehicleId"
      :title="t('home.operationsOverview')"
      :description="t('home.operationsDescription')"
      :vehicles="vehicles"
      :loading="loading"
      :error="error"
      :feed-code="selectedFeedCode"
      :extent="extent"
      :network-features="networkFeatures"
      :detail="detail"
      :detail-loading="detailLoading"
      :recent-reports="recentReports"
      :agency-timezone="agencyTimezone"
      @select-vehicle="(id) => navigateTo(feedPath('/vehicles/' + id))"
      @network-armed="networkArmed = true"
    >
      <template #actions>
        <PollControl :updated-at="newestReport" />
      </template>
    </VehicleOperationsBoard>
  </AppPage>
</template>
