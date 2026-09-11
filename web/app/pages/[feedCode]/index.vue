<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import BarChart from '~/components/BarChart.vue'
import PredictionAccuracyChart from '~/components/PredictionAccuracyChart.vue'
import FeedHealthCard from '~/components/FeedHealthCard.vue'
import FeedOverviewStats from '~/components/FeedOverviewStats.vue'
import FeedProcessingCard from '~/components/FeedProcessingCard.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { buildOverviewStats, adherenceHistogram } from '~/utils/overviewStats'

const { selectedAvlFeedCode } = useFeeds()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
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
</script>

<template>
  <AppPage title="Overview">
    <template #actions>
      <NavbarActions :updated-at="newestReport" />
    </template>

    <FeedOverviewStats />

    <div class="grid gap-4 lg:grid-cols-2">
      <FeedProcessingCard />
      <FeedHealthCard />
    </div>

    <div class="mt-2 flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-bus" class="size-4" />
      Live fleet
    </div>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="live fleet metrics" />

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
          <USkeleton v-for="i in 4" :key="i" class="h-26 w-full" />
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

      <div class="grid gap-4 lg:grid-cols-2">
        <UCard :ui="{ body: 'flex flex-col gap-3' }">
          <div class="flex items-center gap-2 text-sm font-medium text-muted">
            <UIcon name="i-lucide-gauge" class="size-4" />
            Schedule adherence
          </div>
          <USkeleton v-if="loading && !hasData" class="h-32.5 w-full" />
          <BarChart v-else :bars="histogram" :height="130" :format="(n) => `${n}`" />
        </UCard>

        <PredictionAccuracyChart :feed-code="selectedAvlFeedCode" />
      </div>
    </template>
  </AppPage>
</template>
