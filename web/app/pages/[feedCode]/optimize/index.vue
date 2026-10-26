<script setup lang="ts">
import {useOptimizationRunsQuery, type OptimizationRunsQuery} from '~~/generated/graphql'
import {useFeeds} from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import PageState from '~/components/PageState.vue'
import PageSection from '~/components/PageSection.vue'
import NewOptimizationRunDialog from '~/components/optimize/NewOptimizationRunDialog.vue'
import {gtfsDate} from '~/utils/gtfs'

const {selectedFeedCode, feedPath} = useFeeds()
const newOpen = ref(false)

const {result, loading, error} = useOptimizationRunsQuery(
  () => ({feedCode: selectedFeedCode.value ?? '', limit: 50, offset: 0}),
  () => ({enabled: !!selectedFeedCode.value, pollInterval: 5000}),
)

type Run = OptimizationRunsQuery['optimizationRuns'][number]
const runs = computed<Run[]>(() => result.value?.optimizationRuns ?? [])

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}

function runSummary(run: Run) {
  if (run.state === 'SUCCEEDED') return 'Recommendations are ready to review'
  if (run.state === 'FAILED') return run.error ?? 'Analysis failed before recommendations could be produced'
  return 'Analyzing observed vehicle movements'
}
</script>

<template>
  <AppPage title="Optimize" description="Turn observed operations into schedule improvements">
    <template #actions>
      <UButton
        icon="i-lucide-plus"
        color="neutral"
        variant="soft"
        label="New analysis"
        @click="newOpen = true"
      />
    </template>

    <PageState v-if="loading && !runs.length" state="loading" title="Loading analyses" />

    <PageState
      v-else-if="error"
      state="error"
      title="Runs unavailable"
      :description="error.message"
    />

    <PageState
      v-else-if="!runs.length"
      state="empty"
      title="Start with an operational snapshot"
      description="Analyze AVL observations to find the highest-confidence schedule improvements."
      icon="i-lucide-sparkles"
    >
      <template #action>
        <UButton icon="i-lucide-plus" label="Start an analysis" @click="newOpen = true" />
      </template>
    </PageState>

    <PageSection v-else title="Recent analyses" aria-label="Optimization analyses">
      <template #actions>
        <span class="text-sm text-muted">{{ runs.length }} total</span>
      </template>
      <div class="flex flex-col gap-2">
        <NuxtLink
          v-for="run in runs"
          :key="run.id"
          :to="feedPath('/optimize/' + run.id)"
          class="group flex flex-col gap-4 rounded-md border border-default bg-default p-4 transition hover:border-primary/50 hover:shadow-sm sm:flex-row sm:items-center sm:justify-between"
        >
          <div class="flex min-w-0 items-center gap-4">
            <div class="grid size-10 shrink-0 place-items-center rounded-md bg-elevated text-muted transition group-hover:bg-primary/10 group-hover:text-primary">
              <UIcon name="i-lucide-chart-no-axes-combined" class="size-5" />
            </div>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <p class="font-semibold text-highlighted">Analysis #{{ run.id }}</p>
                <UBadge :color="STATE_COLOR[run.state] ?? 'neutral'" variant="subtle">{{ run.state }}</UBadge>
              </div>
              <p class="mt-1 truncate text-sm text-muted">{{ runSummary(run) }}</p>
            </div>
          </div>
          <div class="flex shrink-0 items-center gap-4 text-sm text-muted sm:text-right">
            <div>
              <p class="text-xs text-dimmed">Started</p>
              <p class="mt-1 font-medium text-highlighted">{{ gtfsDate(run.createdAt) }}</p>
            </div>
            <UIcon name="i-lucide-chevron-right" class="size-5 text-dimmed transition group-hover:translate-x-0.5 group-hover:text-primary" />
          </div>
        </NuxtLink>
      </div>
    </PageSection>

    <NewOptimizationRunDialog v-model:open="newOpen" />
  </AppPage>
</template>
