<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import {
  useRoutesQuery,
  useHeadwayRoutePatternsQuery,
  useHeadwayInfoQuery,
  useStopBoardQuery,
  type StopBoardQuery,
} from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import { usePollControl } from '~/composables/usePollControl'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import BarChart from '~/components/BarChart.vue'
import type { Bar } from '~/utils/chart'

const { selectedFeedCode, selectedAvlFeedCode } = useFeeds()
const { intervalMs } = usePollControl()

const selectedRouteId = ref<string | null>(null)
const selectedStopId = ref<string | null>(null)
const selectedDirectionId = ref<number | null>(null)

const { result: routesResult } = useRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const routeOptions = computed(() =>
  (routesResult.value?.routes ?? []).map((r) => ({
    label: r.routeShortName ?? r.routeLongName ?? r.routeId,
    value: r.routeId,
  })),
)

// Stops and directions cascade from the selected route: fetch that route's
// patterns and derive the direction + stop options from them.
const { result: patternsResult, loading: patternsLoading } = useHeadwayRoutePatternsQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '', routeId: selectedRouteId.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value && !!selectedRouteId.value }),
)
const routePatterns = computed(() => patternsResult.value?.tripPatterns ?? [])

const directionOptions = computed(() => {
  const dirs = [
    ...new Set(routePatterns.value.map((p) => p.directionId).filter((d): d is number => d != null)),
  ].sort((a, b) => a - b)
  return [
    { label: 'Either direction', value: null },
    ...dirs.map((d) => ({ label: `Direction ${d}`, value: d })),
  ]
})

const stopOptions = computed(() => {
  const pats =
    selectedDirectionId.value == null
      ? routePatterns.value
      : routePatterns.value.filter((p) => p.directionId === selectedDirectionId.value)
  const seen = new Map<string, { label: string, value: string, order: number }>()
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
    .map(({ label, value }) => ({ label, value }))
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
  () => ({ enabled: querySelected.value, pollInterval: intervalMs.value }),
)
const headway = computed(() => headwayResult.value?.headway ?? null)

const { result: boardResult } = useStopBoardQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    stopId: selectedStopId.value ?? '',
    routeId: selectedRouteId.value,
    directionId: selectedDirectionId.value,
  }),
  () => ({ enabled: querySelected.value, pollInterval: intervalMs.value }),
)
type BoardRow = StopBoardQuery['stopPredictions'][number]
const board = computed<BoardRow[]>(() => boardResult.value?.stopPredictions ?? [])

const boardColumns: TableColumn<BoardRow>[] = [
  { accessorKey: 'scheduledArrival', header: 'Scheduled' },
  { accessorKey: 'predictedArrival', header: 'Predicted' },
  { accessorKey: 'algorithm', header: 'Algorithm' },
  { accessorKey: 'confidenceSec', header: 'Confidence' },
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
): { label: string, color: 'success' | 'neutral' | 'warning' } | null {
  if (sec == null) return null
  if (sec <= 20) return { label: 'High', color: 'success' }
  if (sec <= 60) return { label: 'Fair', color: 'neutral' }
  return { label: 'Low', color: 'warning' }
}

function formatDur(sec: number): string {
  if (sec < 90) return `${Math.round(sec)}s`
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return s ? `${m}m ${s}s` : `${m}m`
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
  return { ratio: w / s, pct: Math.min(100, (w / s) * 100), over: w > s }
})
</script>

<template>
  <UDashboardPanel id="headway">
    <template #header>
      <UDashboardNavbar title="Headway" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <NavbarActions />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="selectedAvlFeedCode">
        <template #left>
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
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <NoRealtimeState v-if="!selectedAvlFeedCode" what="headway analysis" />

      <div v-else-if="!querySelected" class="text-sm text-muted">
        Pick a route and a stop to see headway and upcoming arrivals.
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
          <USkeleton class="h-24 w-full" />
          <USkeleton class="h-40 w-full" />
        </div>

        <div v-else-if="headway" class="grid gap-4 lg:grid-cols-3">
          <UCard class="lg:col-span-1" :ui="{ body: 'flex flex-col gap-3' }">
            <div class="text-xs font-medium text-muted">Current wait vs scheduled headway</div>
            <div class="flex items-baseline gap-2">
              <span class="text-3xl font-semibold text-highlighted">
                {{ headway.waitSec != null ? formatDur(headway.waitSec) : '—' }}
              </span>
              <span class="text-sm text-dimmed">
                / {{ headway.scheduledHeadwaySec != null ? formatDur(headway.scheduledHeadwaySec) : '—' }}
              </span>
            </div>
            <div v-if="waitRatio" class="h-2.5 w-full overflow-hidden rounded-full bg-elevated">
              <div
                class="h-full rounded-full transition-[width]"
                :class="waitRatio.over ? 'bg-error' : 'bg-primary'"
                :style="{ width: `${waitRatio.pct}%` }"
              />
            </div>
            <p v-if="waitRatio" class="text-xs text-dimmed">
              {{ waitRatio.over ? 'Over' : 'Under' }} scheduled headway
              ({{ Math.round(waitRatio.ratio * 100) }}%)
            </p>
          </UCard>

          <UCard class="lg:col-span-2" :ui="{ body: 'flex flex-col gap-3' }">
            <div class="text-xs font-medium text-muted">Recent gaps between arrivals</div>
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

        <UTable :data="board" :columns="boardColumns">
          <template #scheduledArrival-cell="{ row }">{{
            formatTs(row.original.scheduledArrival)
          }}</template>
          <template #predictedArrival-cell="{ row }">{{
            formatTs(row.original.predictedArrival)
          }}</template>
          <template #confidenceSec-cell="{ row }">
            <UBadge
              v-if="confidenceRating(row.original.confidenceSec)"
              :color="confidenceRating(row.original.confidenceSec)!.color"
              variant="subtle"
              size="sm"
              :title="`±1σ uncertainty of the predicted arrival: ±${row.original.confidenceSec}s`"
            >
              {{ confidenceRating(row.original.confidenceSec)!.label }} ·
              &#177;{{ row.original.confidenceSec }}s
            </UBadge>
            <span v-else class="text-dimmed">—</span>
          </template>
        </UTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
