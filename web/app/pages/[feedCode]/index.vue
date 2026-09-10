<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import VehicleMap from '~/components/VehicleMap.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import BarChart from '~/components/BarChart.vue'
import PredictionAccuracyChart from '~/components/PredictionAccuracyChart.vue'
import FeedHealthCard from '~/components/FeedHealthCard.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { buildOverviewStats, adherenceHistogram } from '~/utils/overviewStats'

const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { extent } = useFeedExtent(selectedFeedCode)
const { result: feedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

const hasData = computed(() => vehicles.value.length > 0)
const stats = computed(() => buildOverviewStats(vehicles.value, feedsResult.value?.avlFeeds ?? []))
const histogram = computed(() => adherenceHistogram(vehicles.value))
const newestReport = computed(() =>
  vehicles.value.reduce<string | null>(
    (max, v) => (!max || v.reportTs > max ? v.reportTs : max),
    null,
  ),
)

function onListSelect(vehicleId: string) {
  navigateTo(feedPath(`/vehicles/${vehicleId}`))
}
</script>

<template>
  <AppPage title="Overview">
    <template #actions>
      <NavbarActions :updated-at="newestReport" />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="the fleet overview" />

    <template v-else>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Vehicle feed unavailable"
        :description="error.message"
      />

      <UPageGrid class="gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <template v-if="loading && !hasData">
          <USkeleton v-for="i in 4" :key="i" class="h-[104px] w-full" />
        </template>
        <UCard v-for="stat in stats" v-else :key="stat.label" :ui="{ body: 'flex flex-col gap-2' }">
          <div class="flex items-center gap-2 text-sm text-muted">
            <UIcon :name="stat.icon" class="size-4 shrink-0" />
            {{ stat.label }}
          </div>
          <div class="text-2xl font-semibold text-highlighted">{{ stat.value }}</div>
          <div v-if="stat.hint" class="text-xs text-dimmed">{{ stat.hint }}</div>
        </UCard>
      </UPageGrid>

      <div class="grid gap-4 lg:grid-cols-3">
        <UCard class="lg:col-span-1" :ui="{ body: 'flex flex-col gap-3' }">
          <div class="flex items-center gap-2 text-sm font-medium text-muted">
            <UIcon name="i-lucide-gauge" class="size-4" />
            Schedule adherence
          </div>
          <USkeleton v-if="loading && !hasData" class="h-[130px] w-full" />
          <BarChart v-else :bars="histogram" :height="130" :format="(n) => `${n}`" />
        </UCard>

        <PredictionAccuracyChart class="lg:col-span-1" :feed-code="selectedAvlFeedCode" />

        <FeedHealthCard class="lg:col-span-1" />
      </div>

      <div class="relative h-105 overflow-hidden rounded-lg border border-default">
        <VehicleMap
          :vehicles="vehicles"
          :extent="extent"
          :feed-code="selectedFeedCode"
          @select="onListSelect"
        />
      </div>
    </template>
  </AppPage>
</template>
