<script setup lang="ts">
import { useMutation, useQuery, useQueryCache } from '@pinia/colada'
import type { OptimizationRun, OptimizationRunInput } from '~/api'
import { calendarsQuery, routesQuery } from '~/composables/api/queries/gtfs'
import { optimizationRunsQuery } from '~/composables/api/queries/optimization'
import { startOptimizationRunMutation } from '~/composables/api/mutations/optimization'
import {useFeeds} from '~/composables/useFeeds'
import AppPage from '~/components/shell/AppPage.vue'
import PageState from '~/components/shell/PageState.vue'
import PageSection from '~/components/shell/PageSection.vue'
import NewOptimizationRunDialog from '~/components/optimize/NewOptimizationRunDialog.vue'
import {gtfsDate} from '~/utils/gtfs'

const {selectedFeedCode, feedPath} = useFeeds()
const { t, te, locale } = useI18n()
const newOpen = ref(false)

const {data: runsData, isLoading: loading, error} = useQuery(() => ({
  ...optimizationRunsQuery({ feedCode: selectedFeedCode.value ?? '', limit: 50, offset: 0 }),
  enabled: !!selectedFeedCode.value,
  autoRefetch: 5000,
}))

type Run = OptimizationRun
const runs = computed<Run[]>(() => runsData.value ?? [])

// The dialog's pickers load only while it is open.
const dialogEnabled = computed(() => !!selectedFeedCode.value && newOpen.value)
const {data: routes} = useQuery(() => ({
  ...routesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: dialogEnabled.value,
}))
const {data: calendars} = useQuery(() => ({
  ...calendarsQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: dialogEnabled.value,
}))

const toast = useToast()
const queryCache = useQueryCache()
const {mutateAsync: startRun, isLoading: starting} = useMutation(startOptimizationRunMutation(queryCache))

async function onStart(input: Omit<OptimizationRunInput, 'feedCode'>) {
  try {
    const run = await startRun({ body: { ...input, feedCode: selectedFeedCode.value ?? '' } })
    newOpen.value = false
    if (run?.id) await navigateTo(feedPath('/optimize/' + run.id))
  } catch (e) {
    toast.add({title: t('optimize.startFailed'), description: (e as Error).message, color: 'error'})
  }
}

const STATE_COLOR: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  QUEUED: 'neutral',
  RUNNING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'error',
}

function runSummary(run: Run) {
  if (run.state === 'SUCCEEDED') return t('optimize.summary.succeeded')
  if (run.state === 'FAILED') return run.error ?? t('optimize.summary.failed')
  return t('optimize.summary.running')
}

// i18n-keys: optimize.state.*
function stateLabel(state: string): string {
  const key = `optimize.state.${state}`
  return te(key) ? t(key) : state
}
</script>

<template>
  <AppPage :title="$t('optimize.title')" :description="$t('optimize.description')">
    <template #actions>
      <UButton
        icon="i-lucide-plus"
        color="neutral"
        variant="soft"
        :label="$t('optimize.newAnalysis')"
        @click="newOpen = true"
      />
    </template>

    <PageState v-if="loading && !runs.length" state="loading" :title="$t('optimize.loading')" />

    <PageState
      v-else-if="error"
      state="error"
      :title="$t('optimize.unavailable')"
      :description="error.message"
    />

    <PageState
      v-else-if="!runs.length"
      state="empty"
      :title="$t('optimize.emptyTitle')"
      :description="$t('optimize.emptyBody')"
      icon="i-lucide-sparkles"
    >
      <template #action>
        <UButton icon="i-lucide-plus" :label="$t('optimize.startAnAnalysis')" @click="newOpen = true" />
      </template>
    </PageState>

    <PageSection v-else :title="$t('optimize.recent')" :aria-label="$t('optimize.listAria')">
      <template #actions>
        <span class="text-sm text-muted">{{ $t('optimize.total', {count: runs.length}) }}</span>
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
                <p class="font-semibold text-highlighted">{{ $t('optimize.analysisN', {id: run.id}) }}</p>
                <UBadge :color="STATE_COLOR[run.state] ?? 'neutral'" variant="subtle">{{ stateLabel(run.state) }}</UBadge>
              </div>
              <p class="mt-1 truncate text-sm text-muted">{{ runSummary(run) }}</p>
            </div>
          </div>
          <div class="flex shrink-0 items-center gap-4 text-sm text-muted sm:text-right">
            <div>
              <p class="text-xs text-dimmed">{{ $t('optimize.started') }}</p>
              <p class="mt-1 font-medium text-highlighted">{{ gtfsDate(run.createdAt, locale) }}</p>
            </div>
            <UIcon name="i-lucide-chevron-right" class="size-5 text-dimmed transition group-hover:translate-x-0.5 group-hover:text-primary" />
          </div>
        </NuxtLink>
      </div>
    </PageSection>

    <NewOptimizationRunDialog
      v-model:open="newOpen"
      :routes="routes ?? []"
      :calendars="calendars ?? []"
      :starting="starting"
      @start="onStart"
    />
  </AppPage>
</template>
