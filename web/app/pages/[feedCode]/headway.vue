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
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import BarChart from '~/components/BarChart.vue'
import type {Bar} from '~/utils/chart'

const {selectedFeedCode, selectedAvlFeedCode} = useFeeds()
const {intervalMs} = usePollControl()

const selectedRouteId = ref<string | null>(null)
const selectedStopId = ref<string | null>(null)
const selectedDirectionId = ref<number | null>(null)

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
    {label: 'Either direction', value: null},
    ...dirs.map((d) => ({label: `Direction ${d}`, value: d})),
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
  }),
  () => ({enabled: querySelected.value, pollInterval: intervalMs.value}),
)
type BoardRow = StopBoardQuery['stopPredictions'][number]
const board = computed<BoardRow[]>(() => boardResult.value?.stopPredictions ?? [])

const boardColumns: TableColumn<BoardRow>[] = [
  {accessorKey: 'scheduledArrival', header: 'Scheduled'},
  {accessorKey: 'predictedArrival', header: 'Predicted'},
  {accessorKey: 'algorithm', header: 'Algorithm'},
  {accessorKey: 'confidenceSec', header: 'Confidence'},
]

function formatTs(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso))
}

/**
 * `confidenceSec` is the Kalman filter's ±1σ error on the predicted arrival
 * (only the KALMAN algorithm reports it). Bucket it into a legible rating.
 */
function confidenceRating(
  sec: number | null | undefined,
): { label: string; color: 'success' | 'neutral' | 'warning' } | null {
  if (sec == null) return null
  if (sec <= 20) return {label: 'High', color: 'success'}
  if (sec <= 60) return {label: 'Fair', color: 'neutral'}
  return {label: 'Low', color: 'warning'}
}

function formatDur(sec: number): string {
  if (sec < 90) return `${Math.round(sec)}s`
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return s ? `${m}m ${s}s` : `${m}m`
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
    hint: `Gap ${formatDur(g)}${sched ? ` (scheduled ${formatDur(sched)})` : ''}`,
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
  <AppPage title="Headway" description="Live spacing and arrival reliability at a selected stop">
    <template #actions>
      <NavbarActions/>
    </template>

    <template v-if="selectedAvlFeedCode" #toolbar>
      <div class="flex flex-wrap items-center gap-3 border-b border-default bg-elevated/30 px-4 py-3">
        <div class="mr-1 text-sm font-medium text-highlighted">Analyse service</div>
        <USelectMenu
          :model-value="selectedRouteId"
          :items="routeOptions"
          value-key="value"
          placeholder="Select a route"
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
          placeholder="Select a stop"
          :disabled="!selectedRouteId"
          :loading="patternsLoading && !routePatterns.length"
          class="w-56"
          @update:model-value="selectedStopId = $event"
        />
      </div>
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="headway analysis"/>

    <div v-else-if="!querySelected" class="mx-auto flex max-w-md flex-col items-center gap-3 py-20 text-center">
      <div class="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <UIcon name="i-lucide-timer" class="size-6"/>
      </div>
      <div class="text-base font-medium text-highlighted">Choose a service point</div>
      <p class="text-sm text-muted">Pick a route and stop to review current spacing, recent gaps, and upcoming arrivals.</p>
    </div>

    <UAlert
      v-else-if="headwayError"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Headway unavailable"
      :description="headwayError.message"
    />

    <template v-else>
      <div v-if="headwayLoading && !headway" class="flex flex-col gap-4">
        <USkeleton class="h-24 w-full"/>
        <USkeleton class="h-40 w-full"/>
      </div>

      <div v-else-if="headway" class="flex flex-col gap-4">
        <section class="grid overflow-hidden rounded-xl border border-default bg-default sm:grid-cols-2 xl:grid-cols-4">
          <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Current wait</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ headway.waitSec != null ? formatDur(headway.waitSec) : '—' }}</span>
            <span class="text-xs text-muted">Since the last observed arrival</span>
          </div>
          <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Scheduled headway</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ headway.scheduledHeadwaySec != null ? formatDur(headway.scheduledHeadwaySec) : '—' }}</span>
            <span class="text-xs text-muted">Planned service interval</span>
          </div>
          <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Spacing variance</span>
            <span class="text-2xl font-semibold tabular-nums" :class="varianceSec != null && varianceSec > 0 ? 'text-error' : 'text-highlighted'">{{ varianceSec != null ? formatDelta(varianceSec) : '—' }}</span>
            <span class="text-xs text-muted">{{ varianceSec != null && varianceSec > 0 ? 'Above planned spacing' : 'Against planned spacing' }}</span>
          </div>
          <div class="flex flex-col gap-1 p-4">
            <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Next arrival</span>
            <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ formatTs(nextArrival) }}</span>
            <span class="text-xs text-muted">{{ headway.gapsSec.length }} recent gap{{ headway.gapsSec.length === 1 ? '' : 's' }} sampled</span>
          </div>
        </section>

        <div class="grid gap-4 lg:grid-cols-3">
          <UCard class="lg:col-span-1" :ui="{ body: 'flex flex-col gap-3' }">
            <div class="text-sm font-medium text-highlighted">Current spacing</div>
            <div v-if="waitRatio" class="h-2.5 w-full overflow-hidden rounded-full bg-elevated">
              <div
                class="h-full rounded-full transition-[width]"
                :class="waitRatio.over ? 'bg-error' : 'bg-primary'"
                :style="{ width: `${waitRatio.pct}%` }"
              />
            </div>
            <p v-if="waitRatio" class="text-sm text-muted">
              {{ waitRatio.over ? 'Over' : 'Under' }} scheduled headway ({{ Math.round(waitRatio.ratio * 100) }}%)
            </p>
            <p v-else class="text-sm text-dimmed">Waiting for enough live arrivals to compare spacing.</p>
          </UCard>

          <UCard class="lg:col-span-2" :ui="{ body: 'flex flex-col gap-3' }">
            <div class="text-sm font-medium text-highlighted">Recent gaps between arrivals</div>
            <p v-if="!gapBars.length" class="text-sm text-dimmed">No gaps recorded yet.</p>
            <BarChart
              v-else
              :bars="gapBars"
              :reference-value="headway.scheduledHeadwaySec"
              reference-label="scheduled headway"
              :height="150"
              :format="formatDur"
            />
          </UCard>
        </div>

      <section class="overflow-hidden rounded-xl border border-default bg-default">
        <div class="flex items-center justify-between border-b border-default px-4 py-3">
          <div>
            <div class="text-sm font-medium text-highlighted">Upcoming arrivals</div>
            <div class="text-xs text-muted">Realtime predictions for the selected route and stop</div>
          </div>
          <span class="text-xs text-dimmed">{{ board.length }} arrivals</span>
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
            :title="`±1σ uncertainty of the predicted arrival: ±${row.original.confidenceSec}s`"
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
