<script setup lang="ts">
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
import VehicleOperationsBoard from '~/components/vehicles/VehicleOperationsBoard.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useVehiclePolling } from '~/composables/useVehiclePolling'

const { t } = useI18n()
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
      @select-vehicle="(id) => navigateTo(feedPath('/vehicles/' + id))"
    >
      <template #actions>
        <PollControl :updated-at="newestReport" />
      </template>
    </VehicleOperationsBoard>
  </AppPage>
</template>
