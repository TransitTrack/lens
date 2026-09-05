<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import {
  useRoutesQuery,
  useStopsQuery,
  useHeadwayInfoQuery,
  useStopBoardQuery,
  type StopBoardQuery,
} from '../../generated/graphql'
import { useDashboardSelection } from '../composables/useDashboardSelection'

const { selectedFeedCode } = useDashboardSelection()

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

const { result: stopsResult } = useStopsQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const stopOptions = computed(() =>
  (stopsResult.value?.stops ?? []).map((s) => ({
    label: s.stopName ?? s.stopId,
    value: s.stopId,
  })),
)

const directionOptions = [
  { label: 'Either direction', value: null },
  { label: 'Direction 0', value: 0 },
  { label: 'Direction 1', value: 1 },
]

const querySelected = computed(
  () => !!selectedFeedCode.value && !!selectedStopId.value && !!selectedRouteId.value,
)

const {
  result: headwayResult,
  loading: headwayLoading,
  error: headwayError,
} = useHeadwayInfoQuery(
  () => ({
    feedCode: selectedFeedCode.value ?? '',
    stopId: selectedStopId.value ?? '',
    routeId: selectedRouteId.value ?? '',
    directionId: selectedDirectionId.value,
  }),
  () => ({ enabled: querySelected.value, pollInterval: 15_000 }),
)
const headway = computed(() => headwayResult.value?.headway ?? null)

const { result: boardResult } = useStopBoardQuery(
  () => ({
    feedCode: selectedFeedCode.value ?? '',
    stopId: selectedStopId.value ?? '',
    routeId: selectedRouteId.value,
    directionId: selectedDirectionId.value,
  }),
  () => ({ enabled: querySelected.value, pollInterval: 15_000 }),
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

function formatSec(sec: number | null | undefined): string {
  return sec == null ? '—' : `${sec}s`
}
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-y-auto p-4">
    <div class="flex flex-wrap items-center gap-4">
      <USelectMenu
        :model-value="selectedRouteId"
        :items="routeOptions"
        value-key="value"
        placeholder="Select a route"
        class="w-56"
        @update:model-value="selectedRouteId = $event"
      />
      <USelectMenu
        :model-value="selectedStopId"
        :items="stopOptions"
        value-key="value"
        placeholder="Select a stop"
        class="w-56"
        @update:model-value="selectedStopId = $event"
      />
      <USelectMenu
        :model-value="selectedDirectionId"
        :items="directionOptions"
        value-key="value"
        class="w-44"
        @update:model-value="selectedDirectionId = $event"
      />
    </div>

    <div v-if="!querySelected" class="text-sm text-muted">
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
      <div v-if="headwayLoading && !headway" class="text-sm text-muted">Loading headway…</div>
      <div v-else-if="headway" class="flex flex-wrap gap-6">
        <div>
          <div class="text-xs text-muted">Current wait</div>
          <div class="text-2xl font-semibold">{{ formatSec(headway.waitSec) }}</div>
        </div>
        <div>
          <div class="text-xs text-muted">Scheduled headway</div>
          <div class="text-2xl font-semibold">{{ formatSec(headway.scheduledHeadwaySec) }}</div>
        </div>
        <div>
          <div class="text-xs text-muted">Recent gaps</div>
          <div class="text-2xl font-semibold">
            {{ headway.gapsSec.length ? headway.gapsSec.map(formatSec).join(', ') : '—' }}
          </div>
        </div>
      </div>

      <UTable :data="board" :columns="boardColumns">
        <template #scheduledArrival-cell="{ row }">{{
          formatTs(row.original.scheduledArrival)
        }}</template>
        <template #predictedArrival-cell="{ row }">{{
          formatTs(row.original.predictedArrival)
        }}</template>
        <template #confidenceSec-cell="{ row }">{{
          formatSec(row.original.confidenceSec)
        }}</template>
      </UTable>
    </template>
  </div>
</template>
