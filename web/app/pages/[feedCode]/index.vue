<script setup lang="ts">
import { useQuery, useQueryCache } from '@pinia/colada'
import { liveVehiclesQuery } from '~/composables/api/queries/avl/liveVehiclesQuery'
import { agenciesQuery } from '~/composables/api/queries/gtfs/agenciesQuery'
import { feedGeometryQuery } from '~/composables/api/queries/schedule/feedGeometryQuery'
import { agenciesExtent } from '~/utils/joins/extent'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
import VehicleOperationsBoard from '~/components/vehicles/VehicleOperationsBoard.vue'
import { useFeeds } from '~/composables/useFeeds'
import { usePollControl } from '~/composables/usePollControl'

const { t } = useI18n()
const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { paused, intervalMs } = usePollControl()
const queryCache = useQueryCache()

// Polled queries: pausing stops auto-refetch; a paused query still loads once when its own key has no
// cached data yet, then stays idle. The cache is read by key, not through `data` (the previous key's placeholder).
const pollInterval = () => (paused.value ? false : intervalMs.value)

const { data: vehiclesData, isLoading: loading, error } = useQuery(() => {
  const options = liveVehiclesQuery({ feedCode: selectedAvlFeedCode.value ?? '' })
  return {
    ...options,
    enabled: !!selectedAvlFeedCode.value && (!paused.value || queryCache.getQueryData(options.key) === undefined),
    autoRefetch: pollInterval,
  }
})
const vehicles = computed(() => vehiclesData.value ?? [])

const { data: agencies } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: !!selectedFeedCode.value,
}))
const extent = computed(() => agenciesExtent(agencies.value ?? []))

// The network underlay is read only once the map has loaded and the browser is idle (`networkArmed`).
const networkArmed = ref(false)
const { data: geometry } = useQuery(() => ({
  ...feedGeometryQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: networkArmed.value && !!selectedFeedCode.value,
}))
const networkFeatures = computed(() => (geometry.value as GeoJSON.FeatureCollection | undefined)?.features ?? [])

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
      :title="t('home.operationsOverview')"
      :description="t('home.operationsDescription')"
      :vehicles="vehicles"
      :loading="loading"
      :error="error"
      :feed-code="selectedFeedCode"
      :extent="extent"
      :network-features="networkFeatures"
      @select-vehicle="(id) => navigateTo(feedPath('/vehicles/' + id))"
      @network-armed="networkArmed = true"
    >
      <template #actions>
        <PollControl :updated-at="newestReport" />
      </template>
    </VehicleOperationsBoard>
  </AppPage>
</template>
