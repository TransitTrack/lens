<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import VehicleOperationsBoard from '~/components/VehicleOperationsBoard.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useVehiclePolling } from '~/composables/useVehiclePolling'

const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { extent } = useFeedExtent(selectedFeedCode)

const newestReport = computed(() =>
  vehicles.value.reduce<string | null>(
    (max, v) => (!max || v.reportTs > max ? v.reportTs : max),
    null,
  ),
)
</script>

<template>
  <AppPage full-bleed full-width>
    <NoRealtimeState v-if="!selectedAvlFeedCode" what="live vehicle operations" />
    <VehicleOperationsBoard
      v-else
      title="Operations overview"
      description="Live vehicle positions, trip matching, and service status"
      :vehicles="vehicles"
      :loading="loading"
      :error="error"
      :feed-code="selectedFeedCode"
      :extent="extent"
      @select-vehicle="(id) => navigateTo(feedPath('/vehicles/' + id))"
    >
      <template #actions>
        <NavbarActions :updated-at="newestReport" />
      </template>
    </VehicleOperationsBoard>
  </AppPage>
</template>
