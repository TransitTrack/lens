<script setup lang="ts">
import {useOptimizationRunQuery} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import RecommendationList from '~/components/optimize/RecommendationList.vue'
import ApplyRecommendationsBar from '~/components/optimize/ApplyRecommendationsBar.vue'
import NewOptimizationRunDialog from '~/components/optimize/NewOptimizationRunDialog.vue'
import {gtfsDate} from '~/utils/gtfs'
import {useFeeds} from '~/composables/useFeeds'

const route = useRoute()
const runId = computed(() => String(route.params.runId))
const {feedPath} = useFeeds()

const selectedIds = ref<string[]>([])
const conflictIds = ref<string[]>([])
const recommendationList = ref<InstanceType<typeof RecommendationList> | null>(null)
const newOpen = ref(false)

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

watch(result, (r) => {
  if (!r) return
  const optimizationRun = r.optimizationRun
  if (!optimizationRun || TERMINAL_STATES.includes(optimizationRun.state)) polling.value = false
})

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}
</script>

<template>
  <AppPage :title="`Analysis #${runId}`" description="AVL-informed schedule recommendations">
    <template #leading>
      <UButton
        :to="feedPath('/optimize')"
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        square
        aria-label="Back to analyses"
      />
    </template>
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

    <div v-else class="flex flex-col gap-6">
      <section class="overflow-hidden rounded-xl border border-default bg-elevated/40">
        <div class="flex flex-col gap-5 p-5 sm:p-6 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex items-start gap-4">
            <div
              class="grid size-12 shrink-0 place-items-center rounded-xl"
              :class="{
                'bg-primary/10 text-primary': run.state === 'RUNNING',
                'bg-success/10 text-success': run.state === 'SUCCEEDED',
                'bg-error/10 text-error': run.state === 'FAILED',
                'bg-elevated text-muted': run.state === 'QUEUED',
              }"
            >
              <UIcon :name="run.state === 'SUCCEEDED' ? 'i-lucide-sparkles' : run.state === 'FAILED' ? 'i-lucide-circle-x' : 'i-lucide-chart-no-axes-combined'" class="size-6" />
            </div>
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <UBadge :color="STATE_COLOR[run.state] ?? 'neutral'" variant="subtle">{{ run.state }}</UBadge>
                <span v-if="!isTerminal" class="text-sm text-muted">Live updates enabled</span>
              </div>
              <p class="mt-2 text-base font-medium text-highlighted">
                {{ run.state === 'SUCCEEDED' ? 'Analysis complete — review the suggested schedule changes below.' : run.state === 'FAILED' ? 'This analysis could not be completed.' : 'Reviewing observed vehicle movements against the schedule.' }}
              </p>
            </div>
          </div>
          <dl class="grid grid-cols-2 gap-x-8 gap-y-1 text-sm sm:min-w-72">
            <div>
              <dt class="text-dimmed">Created</dt>
              <dd class="mt-1 font-medium text-highlighted">{{ gtfsDate(run.createdAt) }}</dd>
            </div>
            <div>
              <dt class="text-dimmed">Completed</dt>
              <dd class="mt-1 font-medium text-highlighted">{{ run.completedAt ? gtfsDate(run.completedAt) : 'In progress' }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <UAlert
        v-if="!isTerminal"
        color="info"
        variant="soft"
        icon="i-lucide-loader"
        title="Analysis in progress"
        description="We are comparing observed trips with the published schedule. This page updates automatically."
      />

      <template v-else-if="run.state === 'FAILED'">
        <UAlert
          color="error"
          variant="soft"
          icon="i-lucide-x-circle"
          title="Run failed"
          :description="run.error ?? 'see server logs'"
        />
        <UButton label="Start a new analysis" icon="i-lucide-plus" @click="newOpen = true" />
      </template>

      <template v-else>
        <RecommendationList
          ref="recommendationList"
          v-model:selected="selectedIds"
          v-model:conflict-ids="conflictIds"
          :run-id="runId"
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

    <NewOptimizationRunDialog v-model:open="newOpen" />
  </AppPage>
</template>
