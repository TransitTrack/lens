<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import {
  type AvlReportPageQuery,
  useAvlReportCatalogQuery,
  useAvlReportPageQuery,
} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useUnits } from '~/composables/useUnits'
import {
  assignmentLabel,
  formatReportSource,
  formatReportTimestamp,
  isValidReportRange,
} from '~/utils/avlReport'

interface ReportFilters {
  vehicleId: string | null
  date: string
  startTime: string
  endTime: string
}

const PAGE_SIZE = 100

const { selectedAvlFeedCode } = useFeeds()
const units = useUnits()
const filters = reactive<ReportFilters>({
  vehicleId: null,
  date: '',
  startTime: '00:00',
  endTime: '23:59',
})
const submitted = ref<(ReportFilters & { page: number }) | null>(null)

// The filters panel starts open and collapses once a report has run, so the
// results get the room back; re-opening is still one click away via the
// accordion header.
const FILTERS_ACCORDION_VALUE = 'filters'
const filtersAccordionItems = [{ label: 'Report filters', value: FILTERS_ACCORDION_VALUE }]
const openFilterAccordion = ref<string | undefined>(FILTERS_ACCORDION_VALUE)

const {
  result: catalogResult,
  loading: catalogLoading,
  error: catalogError,
} = useAvlReportCatalogQuery(
  () => ({ feedCode: selectedAvlFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedAvlFeedCode.value }),
)

const timeZone = computed(() => catalogResult.value?.avlReportCatalog.timeZone ?? null)
const vehicleItems = computed(() =>
  (catalogResult.value?.avlReportCatalog.vehicleIds ?? []).map((vehicleId) => ({
    label: vehicleId,
    value: vehicleId,
  })),
)

function todayIn(timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone }).formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

watch(
  timeZone,
  (zone) => {
    if (zone && !filters.date) filters.date = todayIn(zone)
  },
  { immediate: true },
)

const canRun = computed(
  () =>
    !!selectedAvlFeedCode.value &&
    !!filters.vehicleId &&
    isValidReportRange(filters.date, filters.startTime, filters.endTime),
)

function runReport() {
  if (!canRun.value || !filters.vehicleId) return
  const next = { ...filters, vehicleId: filters.vehicleId, page: 1 }
  const isRepeat =
    submitted.value?.vehicleId === next.vehicleId &&
    submitted.value.date === next.date &&
    submitted.value.startTime === next.startTime &&
    submitted.value.endTime === next.endTime &&
    submitted.value.page === next.page
  submitted.value = next
  openFilterAccordion.value = undefined
  if (isRepeat) {
    void refetchPage({
      feedCode: selectedAvlFeedCode.value ?? '',
      ...next,
      pageSize: PAGE_SIZE,
    })
  }
}

function setPage(page: number) {
  if (!submitted.value) return
  submitted.value = { ...submitted.value, page }
}

const {
  result: pageResult,
  loading: pageLoading,
  error: pageError,
  refetch: refetchPage,
} = useAvlReportPageQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    vehicleId: submitted.value?.vehicleId ?? '',
    date: submitted.value?.date ?? '',
    startTime: submitted.value?.startTime ?? '',
    endTime: submitted.value?.endTime ?? '',
    page: submitted.value?.page ?? 1,
    pageSize: PAGE_SIZE,
  }),
  () => ({
    enabled: !!selectedAvlFeedCode.value && !!submitted.value,
    fetchPolicy: 'network-only',
  }),
)

type ReportRow = AvlReportPageQuery['avlReportPage']['items'][number]

const reportPage = computed(() => pageResult.value?.avlReportPage ?? null)
const rows = computed<ReportRow[]>(() => reportPage.value?.items ?? [])
const pageStart = computed(
  () => ((reportPage.value?.page ?? 1) - 1) * (reportPage.value?.pageSize ?? PAGE_SIZE),
)
const pageEnd = computed(() =>
  Math.min(pageStart.value + rows.value.length, reportPage.value?.totalCount ?? 0),
)

const columns: TableColumn<ReportRow>[] = [
  {
    id: 'number',
    header: '#',
    cell: ({ row }) => pageStart.value + row.index + 1,
  },
  { accessorKey: 'vehicleId', header: 'Vehicle ID' },
  {
    accessorKey: 'ts',
    header: 'Time',
    cell: ({ row }) => formatReportTimestamp(row.original.ts, timeZone.value ?? 'UTC'),
  },
  {
    id: 'location',
    header: 'Location',
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-mono text-xs tabular-nums text-muted' },
        `${row.original.position.lat.toFixed(4)}, ${row.original.position.lon.toFixed(4)}`,
      ),
  },
  {
    accessorKey: 'processedAt',
    header: 'Processing time',
    cell: ({ row }) => formatReportTimestamp(row.original.processedAt, timeZone.value ?? 'UTC'),
  },
  {
    accessorKey: 'source',
    header: 'Source',
    cell: ({ row }) => formatReportSource(row.original.source),
  },
  {
    accessorKey: 'speedMps',
    header: 'Speed',
    cell: ({ row }) => units.speed(row.original.speedMps),
  },
  {
    id: 'assignment',
    header: 'Assignment',
    cell: ({ row }) => assignmentLabel(row.original),
  },
]
</script>

<template>
  <AppPage
    title="AVL report"
    description="Historical AVL reports for one vehicle in the selected agency's local time"
  >
    <template #actions>
      <NavbarActions />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="AVL reports" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 py-4">
      <div class="rounded border border-default bg-elevated/20 px-4">
        <UAccordion
          v-model="openFilterAccordion"
          type="single"
          collapsible
          :items="filtersAccordionItems"
        >
          <template #trailing>
            <p v-if="timeZone" class="text-sm text-muted">Agency time: {{ timeZone }}</p>
            <span v-if="catalogLoading" class="text-sm text-dimmed">Loading vehicle history…</span>
            <UIcon
              name="i-lucide-chevron-down"
              class="shrink-0 size-5 ms-auto group-data-[state=open]:rotate-180 transition-transform duration-200 ease-out motion-reduce:transition-none"
            />
          </template>
          <template #body>
            <UAlert
              v-if="catalogError"
              color="error"
              variant="soft"
              icon="i-lucide-alert-triangle"
              title="Couldn't load vehicle history"
              :description="catalogError.message"
            />

            <div
              v-else
              class="grid gap-3 md:grid-cols-[minmax(12rem,1fr)_auto_auto_auto] md:items-end"
            >
              <label class="grid gap-1 text-sm font-medium text-muted">
                Vehicle ID
                <USelectMenu
                  v-model="filters.vehicleId"
                  data-testid="report-vehicle"
                  :items="vehicleItems"
                  value-key="value"
                  searchable
                  placeholder="Select vehicle"
                  icon="i-lucide-bus"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                Date
                <UInput v-model="filters.date" type="date" data-testid="report-date" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                Start time
                <UInput v-model="filters.startTime" type="time" data-testid="report-start-time" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                End time
                <UInput v-model="filters.endTime" type="time" data-testid="report-end-time" />
              </label>
            </div>

            <div class="mt-4 flex items-center justify-between gap-3">
              <p
                v-if="
                  filters.date &&
                  !isValidReportRange(filters.date, filters.startTime, filters.endTime)
                "
                class="text-sm text-error"
              >
                End time must be after start time on the selected date.
              </p>
              <div v-else />
              <UButton
                data-testid="report-run"
                icon="i-lucide-play"
                :disabled="!canRun"
                @click="runReport"
              >
                Run report
              </UButton>
            </div>
          </template>
        </UAccordion>
      </div>

      <section v-if="submitted" class="min-h-0 rounded-lg border border-default bg-default">
        <UAlert
          v-if="pageError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          title="Couldn't load AVL reports"
          :description="pageError.message"
          class="m-4"
        />
        <div v-else-if="pageLoading && !rows.length" class="flex flex-col gap-2 p-4">
          <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
        </div>
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">
          No AVL reports match this time range.
        </p>
        <template v-else>
          <UTable :data="rows" :columns="columns" />
          <div
            class="flex items-center justify-between gap-3 border-t border-default px-4 py-3 text-sm text-muted"
          >
            <span
              >Showing {{ pageStart + 1 }}–{{ pageEnd }} of
              {{ reportPage?.totalCount }} reports</span
            >
            <div class="flex gap-2">
              <UButton
                size="sm"
                color="neutral"
                variant="soft"
                :disabled="(reportPage?.page ?? 1) <= 1"
                @click="setPage((reportPage?.page ?? 1) - 1)"
              >
                Previous
              </UButton>
              <UButton
                size="sm"
                color="neutral"
                variant="soft"
                :disabled="pageEnd >= (reportPage?.totalCount ?? 0)"
                @click="setPage((reportPage?.page ?? 1) + 1)"
              >
                Next
              </UButton>
            </div>
          </div>
        </template>
      </section>
    </div>
  </AppPage>
</template>
