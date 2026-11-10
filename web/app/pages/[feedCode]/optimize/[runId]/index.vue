<script setup lang="ts">
import { useMutation, useQuery, useQueryCache } from '@pinia/colada'
import type { OptimizationRunInput } from '~/api'
import { calendarsQuery, routesQuery } from '~/composables/api/queries/gtfs'
import { optimizationRecommendationsQuery, optimizationRunQuery } from '~/composables/api/queries/optimization'
import { applyOptimizationRecommendationsMutation, startOptimizationRunMutation } from '~/composables/api/mutations/optimization'
import { restProblemOf } from '~/utils/restErrors'

import AppPage from '~/components/shell/AppPage.vue'
import RecommendationList from '~/components/optimize/RecommendationList.vue'
import ApplyRecommendationsBar from '~/components/optimize/ApplyRecommendationsBar.vue'
import NewOptimizationRunDialog from '~/components/optimize/NewOptimizationRunDialog.vue'
import {gtfsDate} from '~/utils/gtfs'
import {useFeeds} from '~/composables/useFeeds'

const route = useRoute()
const runId = computed(() => String(route.params.runId))
const {selectedFeedCode, feedPath} = useFeeds()
const { t, te, locale } = useI18n()

const selectedIds = ref<string[]>([])
const conflictIds = ref<string[]>([])
const newOpen = ref(false)

function onApplied() {
  selectedIds.value = []
  conflictIds.value = []
}

function onConflict(ids: string[]) {
  conflictIds.value = ids
}

const TERMINAL_STATES = ['SUCCEEDED', 'FAILED']

/** Starts true so the first fetch always happens; a watcher below stops polling once the run
 * reaches a terminal state, so a finished run doesn't keep re-fetching every second forever. */
const polling = ref(true)

const {data: runData, isLoading: loading} = useQuery(() => ({
  ...optimizationRunQuery({ id: runId.value }),
  autoRefetch: polling.value ? 1000 : false,
}))

const run = computed(() => runData.value ?? null)
const isTerminal = computed(() => !!run.value && TERMINAL_STATES.includes(run.value.state))

watch(runData, (r) => {
  if (r === undefined) return
  if (!r || TERMINAL_STATES.includes(r.state)) polling.value = false
})

const {data: recommendationsData, isLoading: recommendationsLoading, error: recommendationsError} = useQuery(() => ({
  ...optimizationRecommendationsQuery({ runId: runId.value, status: 'PENDING', offset: 0, limit: 100 }),
  // Only the succeeded-run branch renders RecommendationList.
  enabled: run.value?.state === 'SUCCEEDED',
}))

const toast = useToast()
const queryCache = useQueryCache()
const {mutateAsync: applyRecommendations, isLoading: applying} = useMutation(applyOptimizationRecommendationsMutation(queryCache))

async function onApply(payload: { label: string | null; editor: string }) {
  try {
    const draft = await applyRecommendations({
      path: { runId: runId.value },
      body: { recommendationIds: selectedIds.value, editor: payload.editor, label: payload.label ?? undefined },
    })
    toast.add({
      title: t('optimize.applied'),
      description: draft ? t('optimize.createdDraft', {id: draft.id}) : undefined,
      color: 'success',
      icon: 'i-lucide-check',
      actions: draft
        ? [{label: t('optimize.viewDraft'), onClick: () => navigateTo(feedPath('/drafts/' + draft.id))}]
        : undefined,
    })
    onApplied()
  } catch (e) {
    const first = restProblemOf(e)
    if (first?.code === 'RECOMMENDATION_CONFLICT') {
      onConflict((first.conflictingRecommendationIds ?? []).map(String))
      toast.add({title: t('optimize.conflictTitle'), description: t('optimize.conflictBody'), color: 'error'})
    } else {
      toast.add({title: t('optimize.applyFailed'), description: (e as Error).message, color: 'error'})
    }
  }
}

// The new-analysis dialog (offered when a run failed) loads its pickers only while open.
const dialogEnabled = computed(() => !!selectedFeedCode.value && newOpen.value)
const {data: routes} = useQuery(() => ({
  ...routesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: dialogEnabled.value,
}))
const {data: calendars} = useQuery(() => ({
  ...calendarsQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: dialogEnabled.value,
}))
const {mutateAsync: startRun, isLoading: starting} = useMutation(startOptimizationRunMutation(queryCache))

async function onStart(input: Omit<OptimizationRunInput, 'feedCode'>) {
  try {
    const started = await startRun({ body: { ...input, feedCode: selectedFeedCode.value ?? '' } })
    newOpen.value = false
    if (started?.id) await navigateTo(feedPath('/optimize/' + started.id))
  } catch (e) {
    toast.add({title: t('optimize.startFailed'), description: (e as Error).message, color: 'error'})
  }
}

// i18n-keys: optimize.state.*
function stateLabel(state: string): string {
  const key = `optimize.state.${state}`
  return te(key) ? t(key) : state
}

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}
</script>

<template>
  <AppPage :title="$t('optimize.analysisN', {id: runId})" :description="$t('optimize.subtitle')">
    <template #leading>
      <UButton
        :to="feedPath('/optimize')"
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        square
        :aria-label="$t('optimize.backToAnalyses')"
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
      :title="$t('optimize.notFound')"
    />

    <div v-else class="flex flex-col gap-6">
      <section class="overflow-hidden rounded-md border border-default bg-elevated/40">
        <div class="flex flex-col gap-5 p-5 sm:p-6 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex items-start gap-4">
            <div
              class="grid size-12 shrink-0 place-items-center rounded-md"
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
                <UBadge :color="STATE_COLOR[run.state] ?? 'neutral'" variant="subtle">{{ stateLabel(run.state) }}</UBadge>
                <span v-if="!isTerminal" class="text-sm text-muted">{{ $t('optimize.liveUpdates') }}</span>
              </div>
              <p class="mt-2 text-base font-medium text-highlighted">
                {{ run.state === 'SUCCEEDED' ? $t('optimize.headline.succeeded') : run.state === 'FAILED' ? $t('optimize.headline.failed') : $t('optimize.headline.running') }}
              </p>
            </div>
          </div>
          <dl class="grid grid-cols-2 gap-x-8 gap-y-1 text-sm sm:min-w-72">
            <div>
              <dt class="text-dimmed">{{ $t('optimize.created') }}</dt>
              <dd class="mt-1 font-medium text-highlighted">{{ gtfsDate(run.createdAt, locale) }}</dd>
            </div>
            <div>
              <dt class="text-dimmed">{{ $t('optimize.completed') }}</dt>
              <dd class="mt-1 font-medium text-highlighted">{{ run.completedAt ? gtfsDate(run.completedAt, locale) : $t('optimize.inProgress') }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <UAlert
        v-if="!isTerminal"
        color="info"
        variant="soft"
        icon="i-lucide-loader"
        :title="$t('optimize.inProgressTitle')"
        :description="$t('optimize.inProgressBody')"
      />

      <template v-else-if="run.state === 'FAILED'">
        <UAlert
          color="error"
          variant="soft"
          icon="i-lucide-x-circle"
          :title="$t('optimize.runFailed')"
          :description="run.error ?? $t('optimize.seeLogs')"
        />
        <UButton :label="$t('optimize.startNew')" icon="i-lucide-plus" @click="newOpen = true" />
      </template>

      <template v-else>
        <RecommendationList
          v-model:selected="selectedIds"
          v-model:conflict-ids="conflictIds"
          :recommendations="recommendationsData ?? []"
          :loading="recommendationsLoading"
          :error="recommendationsError"
        />
        <ApplyRecommendationsBar
          v-if="selectedIds.length"
          :selected-ids="selectedIds"
          :applying="applying"
          @apply="onApply"
        />
      </template>
    </div>

    <NewOptimizationRunDialog
      v-model:open="newOpen"
      :routes="routes ?? []"
      :calendars="calendars ?? []"
      :starting="starting"
      @start="onStart"
    />
  </AppPage>
</template>
