<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import {
  useRoutesQuery,
  useHeadwayRoutePatternsQuery,
  useHeadwayInfoQuery,
  useStopBoardQuery,
  type StopBoardQuery,
} from '~~/generated/graphql'
import {useFeeds} from '~/composables/useFeeds'
import {usePollControl} from '~/composables/usePollControl'
import {formatClock} from '~/utils/clock'
import AppPage from '~/components/AppPage.vue'
import PageState from '~/components/PageState.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import BarChart from '~/components/BarChart.vue'
import type {Bar} from '~/utils/chart'

const {t, locale} = useI18n()
const {selectedFeed, selectedFeedCode, selectedAvlFeedCode} = useFeeds()
const {intervalMs} = usePollControl()

const selectedRouteId = ref<string | null>(null)
const selectedStopId = ref<string | null>(null)
const selectedDirectionId = ref<number | null>(null)
const selectedAlgorithm = ref<string | null>(null)

function algorithmLabel(algorithm: string): string {
  return algorithm
    .toLowerCase()
    .split('_')
    .map((w) => w[0]!.toUpperCase() + w.slice(1))
    .join(' ')
}
const algorithmOptions = computed(() =>
  (selectedFeed.value?.predictionAlgorithms ?? []).map((a) => ({label: algorithmLabel(a), value: a})),
)
// A feed configured with a single algorithm has nothing to pick between; clear
// the selection if the feed changes and no longer offers the current pick.
watch(algorithmOptions, (opts) => {
  if (selectedAlgorithm.value && !opts.some((o) => o.value === selectedAlgorithm.value)) {
    selectedAlgorithm.value = null
  }
})

const {result: routesResult} = useRoutesQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
)
const routeOptions = computed(() =>
  (routesResult.value?.routes ?? []).map((r) => ({
    label: r.routeShortName ?? r.routeLongName ?? r.routeId,
    value: r.routeId,
  })),
)

// Stops and directions cascade from the selected route: fetch that route's
// patterns and derive the direction + stop options from them.
const {result: patternsResult, loading: patternsLoading} = useHeadwayRoutePatternsQuery(
  () => ({feedCode: selectedFeedCode.value ?? '', routeId: selectedRouteId.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value && !!selectedRouteId.value}),
)
const routePatterns = computed(() => patternsResult.value?.tripPatterns ?? [])

const directionOptions = computed(() => {
  const dirs = [
    ...new Set(routePatterns.value.map((p) => p.directionId).filter((d): d is number => d != null)),
  ].sort((a, b) => a - b)
  return [
    {label: t('reports.headway.eitherDirection'), value: null},
    ...dirs.map((d) => ({label: t('vehicles.detail.fact.directionN', {n: d}), value: d})),
  ]
})

const stopOptions = computed(() => {
  const pats =
    selectedDirectionId.value == null
      ? routePatterns.value
      : routePatterns.value.filter((p) => p.directionId === selectedDirectionId.value)
  const seen = new Map<string, { label: string; value: string; order: number }>()
  for (const p of pats) {
    for (const sp of p.stopPaths) {
      if (!seen.has(sp.stopId)) {
        seen.set(sp.stopId, {
          label: sp.stop?.stopName ?? sp.stopId,
          value: sp.stopId,
          order: sp.stopPathIndex,
        })
      }
    }
  }
  return [...seen.values()]
    .sort((a, b) => a.order - b.order)
    .map(({label, value}) => ({label, value}))
})

// Selecting a route resets the downstream picks; a stop that falls out of the
// filtered list (route or direction changed) is cleared.
watch(selectedRouteId, () => {
  selectedDirectionId.value = null
  selectedStopId.value = null
})
watch(stopOptions, (opts) => {
  if (selectedStopId.value && !opts.some((o) => o.value === selectedStopId.value)) {
    selectedStopId.value = null
  }
})

const querySelected = computed(
  () => !!selectedAvlFeedCode.value && !!selectedStopId.value && !!selectedRouteId.value,
)

const {
  result: headwayResult,
  loading: headwayLoading,
  error: headwayError,
} = useHeadwayInfoQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    stopId: selectedStopId.value ?? '',
    routeId: selectedRouteId.value ?? '',
    directionId: selectedDirectionId.value,
    algorithm: selectedAlgorithm.value,
  }),
  () => ({enabled: querySelected.value, pollInterval: intervalMs.value}),
)
const headway = computed(() => headwayResult.value?.headway ?? null)

const {result: boardResult} = useStopBoardQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    stopId: selectedStopId.value ?? '',
    routeId: selectedRouteId.value,
    directionId: selectedDirectionId.value,
    algorithm: selectedAlgorithm.value,
  }),
  () => ({enabled: querySelected.value, pollInterval: intervalMs.value}),
)
type BoardRow = StopBoardQuery['stopPredictions'][number]
const board = computed<BoardRow[]>(() => boardResult.value?.stopPredictions ?? [])

const boardColumns = computed<TableColumn<BoardRow>[]>(() => [
  {accessorKey: 'scheduledArrival', header: t('vehicles.field.scheduled')},
  {accessorKey: 'predictedArrival', header: t('vehicles.detail.predicted')},
  {accessorKey: 'algorithm', header: t('reports.headway.colAlgorithm')},
  {accessorKey: 'confidenceSec', header: t('reports.headway.colSpread')},
])

function formatTs(iso: string | null | undefined): string {
  return formatClock(iso, { seconds: true, locale: locale.value })
}

/**
 * `confidenceSec` is the ±1σ error on the predicted arrival, reported by
 * KALMAN (its filter's error) and SMILE_GBM (from its quantile spread).
 * Bucket it into a legible rating.
 */
function confidenceRating(
  sec: number | null | undefined,
): { label: string; color: 'success' | 'neutral' | 'warning' } | null {
  if (sec == null) return null
  if (sec <= 20) return {label: t('reports.headway.confidenceSmall'), color: 'success'}
  if (sec <= 60) return {label: t('reports.headway.confidenceMedium'), color: 'neutral'}
  return {label: t('reports.headway.confidenceLarge'), color: 'warning'}
}

function formatDur(sec: number): string {
  if (sec < 90) return t('reports.duration.seconds', {n: Math.round(sec)})
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return s ? t('reports.duration.minutesSeconds', {m, s}) : t('reports.duration.minutes', {m})
}

function formatDelta(sec: number): string {
  return `${sec > 0 ? '+' : sec < 0 ? '−' : ''}${formatDur(Math.abs(sec))}`
}

const gapBars = computed<Bar[]>(() => {
  const gaps = headway.value?.gapsSec ?? []
  const sched = headway.value?.scheduledHeadwaySec ?? null
  return gaps.map((g, i) => ({
    label: `#${gaps.length - i}`,
    value: g,
    color: sched && g > sched * 1.25 ? '#ef4444' : sched && g < sched * 0.6 ? '#f59e0b' : '#22c55e',
    hint: sched
      ? t('reports.headway.gapHintScheduled', {gap: formatDur(g), scheduled: formatDur(sched)})
      : t('reports.headway.gapHint', {gap: formatDur(g)}),
  }))
})

/** current wait as a fraction of the scheduled headway, clamped for the gauge */
const waitRatio = computed(() => {
  const w = headway.value?.waitSec
  const s = headway.value?.scheduledHeadwaySec
  if (w == null || !s) return null
  return {ratio: w / s, pct: Math.min(100, (w / s) * 100), over: w > s}
})

const varianceSec = computed(() => {
  const wait = headway.value?.waitSec
  const scheduled = headway.value?.scheduledHeadwaySec
  return wait != null && scheduled != null ? wait - scheduled : null
})

const nextArrival = computed(() => board.value[0]?.predictedArrival ?? board.value[0]?.scheduledArrival)
</script>

<template>
  <AppPage :title="$t('nav.headway')" :description="$t('reports.headway.description')">
    <template #actions>
      <NavbarActions/>
    </template>

    <template v-if="selectedAvlFeedCode" #toolbar>
      <div class="flex flex-wrap items-center gap-3 py-3">
        <div class="mr-1 text-sm font-medium text-highlighted">{{ $t('reports.headway.analyseService') }}</div>
        <USelectMenu
          :model-value="selectedRouteId"
          :items="routeOptions"
          value-key="value"
          :placeholder="$t('reports.headway.selectRoute')"
          class="w-56"
          @update:model-value="selectedRouteId = $event"
        />
        <USelectMenu
          :model-value="selectedDirectionId"
          :items="directionOptions"
          value-key="value"
          :disabled="!selectedRouteId"
          :loading="patternsLoading && !routePatterns.length"
          class="w-44"
          @update:model-value="selectedDirectionId = $event"
        />
        <USelectMenu
          :model-value="selectedStopId"
          :items="stopOptions"
          value-key="value"
          :placeholder="$t('reports.headway.selectStop')"
          :disabled="!selectedRouteId"
          :loading="patternsLoading && !routePatterns.length"
          class="w-56"
          @update:model-value="selectedStopId = $event"
        />
        <USelectMenu
          v-if="algorithmOptions.length > 1"
          :model-value="selectedAlgorithm"
          :items="algorithmOptions"
          value-key="value"
          :placeholder="$t('reports.headway.algorithm')"
          class="w-48"
          @update:model-value="selectedAlgorithm = $event"
        />
      </div>
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" :what="$t('reports.noRealtime.headway')"/>

    <PageState
      v-else-if="!querySelected"
      state="empty"
      :title="$t('reports.headway.chooseTitle')"
      :description="$t('reports.headway.chooseBody')"
      icon="i-lucide-timer"
    />

    <UAlert
      v-else-if="headwayError"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="$t('reports.headway.unavailable')"
      :description="headwayError.message"
    />

    <template v-else>
      <div v-if="headwayLoading && !headway" class="flex flex-col gap-4">
        <USkeleton class="h-24 w-full"/>
        <USkeleton class="h-40 w-full"/>
      </div>

      <div v-else-if="headway" class="flex flex-col gap-4">
        <section class="app-frame grid sm:grid-cols-2 xl:grid-cols-4">
          <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{ $t('reports.headway.currentWait') }}</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ headway.waitSec != null ? formatDur(headway.waitSec) : '—' }}</span>
            <span class="text-xs text-muted">{{ $t('reports.headway.sinceLastArrival') }}</span>
          </div>
          <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{ $t('reports.headway.scheduledHeadway') }}</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ headway.scheduledHeadwaySec != null ? formatDur(headway.scheduledHeadwaySec) : '—' }}</span>
            <span class="text-xs text-muted">{{ $t('reports.headway.plannedInterval') }}</span>
          </div>
          <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{ $t('reports.headway.spacingVariance') }}</span>
            <span class="text-2xl font-semibold tabular-nums" :class="varianceSec != null && varianceSec > 0 ? 'text-error' : 'text-highlighted'">{{ varianceSec != null ? formatDelta(varianceSec) : '—' }}</span>
            <span class="text-xs text-muted">{{ varianceSec != null && varianceSec > 0 ? $t('reports.headway.aboveSpacing') : $t('reports.headway.againstSpacing') }}</span>
          </div>
          <div class="flex flex-col gap-1 p-4">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{ $t('reports.headway.nextArrival') }}</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ formatTs(nextArrival) }}</span>
            <span class="text-xs text-muted">{{ $t('reports.headway.recentGaps', { count: headway.gapsSec.length }, headway.gapsSec.length) }}</span>
          </div>
        </section>

        <div class="grid gap-4 lg:grid-cols-3">
          <section class="app-frame flex flex-col gap-3 p-4 lg:col-span-1">
            <div class="text-sm font-medium text-highlighted">{{ $t('reports.headway.currentSpacing') }}</div>
            <div v-if="waitRatio" class="h-2.5 w-full overflow-hidden rounded-full bg-elevated">
              <div
                class="h-full rounded-full transition-[width]"
                :class="waitRatio.over ? 'bg-error' : 'bg-primary'"
                :style="{ width: `${waitRatio.pct}%` }"
              />
            </div>
            <p v-if="waitRatio" class="text-sm text-muted">
              {{ $t(waitRatio.over ? 'reports.headway.overHeadway' : 'reports.headway.underHeadway', { pct: Math.round(waitRatio.ratio * 100) }) }}
            </p>
            <p v-else class="text-sm text-dimmed">{{ $t('reports.headway.waiting') }}</p>
          </section>

          <section class="app-frame flex flex-col gap-3 p-4 lg:col-span-2">
            <div class="text-sm font-medium text-highlighted">{{ $t('reports.headway.gapsTitle') }}</div>
            <p v-if="!gapBars.length" class="text-sm text-dimmed">{{ $t('reports.headway.noGaps') }}</p>
            <BarChart
              v-else
              :bars="gapBars"
              :reference-value="headway.scheduledHeadwaySec"
              :reference-label="$t('reports.headway.referenceLabel')"
              :height="150"
              :format="formatDur"
            />
          </section>
        </div>

      <section class="app-frame">
        <div class="flex items-center justify-between border-b border-default px-4 py-3">
          <div>
            <div class="text-sm font-medium text-highlighted">{{ $t('reports.headway.upcoming') }}</div>
            <div class="text-xs text-muted">{{ $t('reports.headway.upcomingBody') }}</div>
          </div>
          <span class="text-xs text-dimmed">{{ $t('reports.headway.arrivalsCount', { count: board.length }, board.length) }}</span>
        </div>
        <UTable :data="board" :columns="boardColumns">
        <template #scheduledArrival-cell="{ row }">{{
            formatTs(row.original.scheduledArrival)
          }}
        </template>
        <template #predictedArrival-cell="{ row }">{{
            formatTs(row.original.predictedArrival)
          }}
        </template>
        <template #confidenceSec-cell="{ row }">
          <UBadge
            v-if="confidenceRating(row.original.confidenceSec)"
            :color="confidenceRating(row.original.confidenceSec)!.color"
            variant="subtle"
            :title="$t('reports.headway.confidenceTitle', { sec: row.original.confidenceSec })"
          >
            {{ confidenceRating(row.original.confidenceSec)!.label }} · &#177;{{
              row.original.confidenceSec
            }}s
          </UBadge>
          <span v-else class="text-dimmed">—</span>
        </template>
        </UTable>
      </section>
      </div>
    </template>
  </AppPage>
</template>
