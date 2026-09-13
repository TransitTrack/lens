<script setup lang="ts">
import {useOptimizationRunQuery} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import RecommendationList from '~/components/optimize/RecommendationList.vue'
import ApplyRecommendationsBar from '~/components/optimize/ApplyRecommendationsBar.vue'
import {gtfsDate} from '~/utils/gtfs'

const route = useRoute()
const runId = computed(() => String(route.params.runId))

const selectedIds = ref<string[]>([])
const conflictIds = ref<string[]>([])
const recommendationList = ref<InstanceType<typeof RecommendationList> | null>(null)

function onApplied() {
  selectedIds.value = []
  conflictIds.value = []
  recommendationList.value?.refetch()
}

function onConflict(ids: string[]) {
  conflictIds.value = ids
}

const TERMINAL_STATES = ['SUCCEEDED', 'FAILED']

/** Starts true so the first fetch always happens; a watcher below stops polling once the run
 * reaches a terminal state, so a finished run doesn't keep re-fetching every second forever. */
const polling = ref(true)

const {result, loading} = useOptimizationRunQuery(
  () => ({id: runId.value}),
  () => ({pollInterval: polling.value ? 1000 : 0}),
)

const run = computed(() => result.value?.optimizationRun ?? null)
const isTerminal = computed(() => !!run.value && TERMINAL_STATES.includes(run.value.state))

watch(run, (r) => {
  if (r && TERMINAL_STATES.includes(r.state)) polling.value = false
})

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}
</script>

<template>
  <AppPage :title="`Run #${runId}`">
    <div v-if="loading && !run" class="flex flex-col gap-4">
      <USkeleton class="h-10 w-full" />
    </div>

    <UAlert
      v-else-if="!run"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Run not found"
    />

    <div v-else class="flex flex-col gap-4">
      <div class="flex items-center gap-3">
        <UBadge :color="STATE_COLOR[run.state] ?? 'neutral'" variant="subtle">{{ run.state }}</UBadge>
        <span class="text-sm text-muted">created {{ gtfsDate(run.createdAt) }}</span>
        <span v-if="run.completedAt" class="text-sm text-muted">· completed {{ gtfsDate(run.completedAt) }}</span>
      </div>

      <UAlert
        v-if="!isTerminal"
        color="info"
        variant="soft"
        icon="i-lucide-loader"
        title="Analyzing…"
        description="This page updates automatically."
      />

      <UAlert
        v-else-if="run.state === 'FAILED'"
        color="error"
        variant="soft"
        icon="i-lucide-x-circle"
        title="Run failed"
        :description="run.error ?? 'see server logs'"
      />

      <template v-else>
        <RecommendationList
          ref="recommendationList"
          :run-id="runId"
          v-model:selected="selectedIds"
          v-model:conflict-ids="conflictIds"
        />
        <ApplyRecommendationsBar
          v-if="selectedIds.length"
          :run-id="runId"
          :selected-ids="selectedIds"
          @applied="onApplied"
          @conflict="onConflict"
        />
      </template>
    </div>
  </AppPage>
</template>
