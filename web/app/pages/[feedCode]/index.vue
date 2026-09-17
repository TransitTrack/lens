<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import BarChart from '~/components/BarChart.vue'
import PredictionAccuracyChart from '~/components/PredictionAccuracyChart.vue'
import FeedHealthCard from '~/components/FeedHealthCard.vue'
import FeedOverviewStats from '~/components/FeedOverviewStats.vue'
import FeedProcessingCard from '~/components/FeedProcessingCard.vue'
import NetworkPulse from '~/components/NetworkPulse.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { adherenceHistogram } from '~/utils/overviewStats'

const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { extent } = useFeedExtent(selectedFeedCode)
const { result: feedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

const hasData = computed(() => vehicles.value.length > 0)
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

    <NetworkPulse
      :vehicles="vehicles"
      :loading="loading"
      :error="error"
      :has-realtime-source="!!selectedAvlFeedCode"
      :selected-avl-feed-code="selectedAvlFeedCode"
      :feed-code="selectedFeedCode"
      :extent="extent"
      :updated-at="newestReport"
      :feeds="feedsResult?.avlFeeds ?? []"
      @select-vehicle="(id) => navigateTo(feedPath('/vehicles/' + id))"
    />

    <FeedOverviewStats />

    <section
      class="dashboard-panel"
      style="--dashboard-panel-delay: 70ms"
      aria-labelledby="network-health-heading"
    >
      <h2 id="network-health-heading" class="mb-3 text-lg font-semibold text-highlighted">Network health</h2>
      <div class="grid gap-4 lg:grid-cols-2">
        <FeedProcessingCard />
        <FeedHealthCard />
      </div>
    </section>

    <section
      class="dashboard-panel"
      style="--dashboard-panel-delay: 140ms"
      aria-labelledby="analytics-heading"
    >
      <h2 id="analytics-heading" class="mb-3 text-lg font-semibold text-highlighted">Analytics</h2>
      <div class="grid gap-4 lg:grid-cols-2">
        <UCard :ui="{ body: 'flex flex-col gap-3' }">
          <div class="flex items-center gap-2 text-sm font-medium text-muted">
            <UIcon name="i-lucide-gauge" class="size-4" />
            Schedule adherence
          </div>
          <USkeleton v-if="loading && !hasData" class="h-32.5 w-full" />
          <BarChart
            v-else
            :bars="histogram"
            :height="130"
            :format="(n) => `${n}`"
            animate-updates
          />
        </UCard>

        <PredictionAccuracyChart :feed-code="selectedAvlFeedCode" />
      </div>
    </section>
  </AppPage>
</template>
