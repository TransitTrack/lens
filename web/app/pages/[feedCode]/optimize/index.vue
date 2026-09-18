<script setup lang="ts">
import {useOptimizationRunsQuery, type OptimizationRunsQuery} from '~~/generated/graphql'
import {useFeeds} from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
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

    <div v-if="loading && !runs.length" class="flex flex-col gap-4">
      <USkeleton class="h-10 w-full" />
      <USkeleton class="h-40 w-full" />
    </div>

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Runs unavailable"
      :description="error.message"
    />

    <div v-else-if="!runs.length" class="flex flex-col items-center gap-4 rounded-xl border border-dashed border-default py-16 text-center">
      <div class="grid size-12 place-items-center rounded-xl bg-primary/10 text-primary">
        <UIcon name="i-lucide-sparkles" class="size-6" />
      </div>
      <div>
        <p class="font-medium text-highlighted">Start with an operational snapshot</p>
        <p class="mt-1 text-sm text-muted">Analyze AVL observations to find the highest-confidence schedule improvements.</p>
      </div>
      <UButton icon="i-lucide-plus" label="Start an analysis" @click="newOpen = true" />
    </div>

    <section v-else class="flex flex-col gap-3" aria-label="Optimization analyses">
      <div class="flex items-center justify-between px-1">
        <p class="text-sm font-medium text-highlighted">Recent analyses</p>
        <p class="text-sm text-muted">{{ runs.length }} total</p>
      </div>
      <NuxtLink
        v-for="run in runs"
        :key="run.id"
        :to="feedPath('/optimize/' + run.id)"
        class="group flex flex-col gap-4 rounded-xl border border-default bg-default p-4 transition hover:border-primary/50 hover:shadow-sm sm:flex-row sm:items-center sm:justify-between sm:p-5"
      >
        <div class="flex min-w-0 items-center gap-4">
          <div class="grid size-10 shrink-0 place-items-center rounded-lg bg-elevated text-muted transition group-hover:bg-primary/10 group-hover:text-primary">
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
    </section>

    <NewOptimizationRunDialog v-model:open="newOpen" />
  </AppPage>
</template>
