<script setup lang="ts">
import { useAvlReportCatalogQuery, useAvlReportPageQuery } from '~/composables/useRestRealtime'
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import type { AvlReportPageQuery } from '~/types/restViews'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
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

const { t, locale } = useI18n()
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
const filtersAccordionItems = computed(() => [
  { label: t('reports.filters.title'), value: FILTERS_ACCORDION_VALUE },
])
const openFilterAccordion = ref<string | undefined>(FILTERS_ACCORDION_VALUE)

const {
  result: catalogResult,
  loading: catalogLoading,
  error: catalogError,
} = useAvlReportCatalogQuery(() => ({
  variables: { feedCode: selectedAvlFeedCode.value ?? '' },
  enabled: !!selectedAvlFeedCode.value,
}))

const timeZone = computed(() => catalogResult.value?.avlReportCatalog.timeZone ?? null)
const vehicleItems = computed(() =>
  (catalogResult.value?.avlReportCatalog.vehicleIds ?? []).map((vehicleId) => ({
    label: vehicleId,
    value: vehicleId,
  })),
)

function todayIn(timeZone: string): string {
  // Machine format: 'en-CA' yields ISO yyyy-mm-dd for the API and date inputs, so it must not follow the UI locale.
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
} = useAvlReportPageQuery(() => ({
  variables: { feedCode: selectedAvlFeedCode.value ?? '', vehicleId: submitted.value?.vehicleId ?? '', date: submitted.value?.date ?? '', startTime: submitted.value?.startTime ?? '', endTime: submitted.value?.endTime ?? '', page: submitted.value?.page ?? 1, pageSize: PAGE_SIZE },
  enabled: !!selectedAvlFeedCode.value && !!submitted.value, fetchPolicy: 'network-only',
}))

type ReportRow = AvlReportPageQuery['avlReportPage']['items'][number]

const reportPage = computed(() => pageResult.value?.avlReportPage ?? null)
const rows = computed<ReportRow[]>(() => reportPage.value?.items ?? [])
const pageStart = computed(
  () => ((reportPage.value?.page ?? 1) - 1) * (reportPage.value?.pageSize ?? PAGE_SIZE),
)
const pageEnd = computed(() =>
  Math.min(pageStart.value + rows.value.length, reportPage.value?.totalCount ?? 0),
)

const columns = computed<TableColumn<ReportRow>[]>(() => [
  {
    id: 'number',
    header: '#',
    cell: ({ row }) => pageStart.value + row.index + 1,
  },
  { accessorKey: 'vehicleId', header: t('vehicles.field.vehicleId') },
  {
    accessorKey: 'ts',
    header: t('vehicles.avl.column.time'),
    cell: ({ row }) => formatReportTimestamp(row.original.ts, timeZone.value ?? 'UTC', locale.value),
  },
  {
    id: 'location',
    header: t('reports.col.location'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-mono text-xs tabular-nums text-muted' },
        `${row.original.position.lat.toFixed(4)}, ${row.original.position.lon.toFixed(4)}`,
      ),
  },
  {
    accessorKey: 'processedAt',
    header: t('reports.col.processingTime'),
    cell: ({ row }) => formatReportTimestamp(row.original.processedAt, timeZone.value ?? 'UTC', locale.value),
  },
  {
    accessorKey: 'source',
    header: t('reports.col.source'),
    cell: ({ row }) => formatReportSource(row.original.source),
  },
  {
    accessorKey: 'speedMps',
    header: t('vehicles.field.speed'),
    cell: ({ row }) => units.speed(row.original.speedMps),
  },
  {
    id: 'assignment',
    header: t('reports.col.assignment'),
    cell: ({ row }) => assignmentLabel(row.original, t),
  },
])
</script>

<template>
  <AppPage
    :title="$t('reports.avl.title')"
    :description="$t('reports.avl.description')"
  >
    <template #actions>
      <PollControl />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" :what="$t('reports.noRealtime.avl')" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 py-4">
      <div class="app-frame bg-elevated/20 px-4">
        <UAccordion
          v-model="openFilterAccordion"
          type="single"
          collapsible
          :items="filtersAccordionItems"
        >
          <template #trailing>
            <p v-if="timeZone" class="text-sm text-muted">
              {{ $t('reports.filters.agencyTime', { zone: timeZone }) }}
            </p>
            <span v-if="catalogLoading" class="text-sm text-dimmed">{{ $t('reports.filters.loadingVehicleHistory') }}</span>
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
              :title="$t('reports.error.vehicleHistory')"
              :description="catalogError.message"
            />

            <div
              v-else
              class="grid gap-3 md:grid-cols-[minmax(12rem,1fr)_auto_auto_auto] md:items-end"
            >
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('vehicles.field.vehicleId') }}
                <USelectMenu
                  v-model="filters.vehicleId"
                  data-testid="report-vehicle"
                  :items="vehicleItems"
                  value-key="value"
                  searchable
                  :placeholder="$t('reports.filters.selectVehicle')"
                  icon="i-lucide-bus"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('explore.col.date') }}
                <UInput v-model="filters.date" type="date" data-testid="report-date" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.startTime') }}
                <UInput v-model="filters.startTime" type="time" data-testid="report-start-time" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.endTime') }}
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
                {{ $t('reports.filters.endTimeBeforeStart') }}
              </p>
              <div v-else />
              <UButton
                data-testid="report-run"
                icon="i-lucide-play"
                :disabled="!canRun"
                @click="runReport"
              >
                {{ $t('reports.filters.run') }}
              </UButton>
            </div>
          </template>
        </UAccordion>
      </div>

      <section v-if="submitted" class="min-h-0 app-frame">
        <UAlert
          v-if="pageError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          :title="$t('reports.error.avl')"
          :description="pageError.message"
          class="m-4"
        />
        <div v-else-if="pageLoading && !rows.length" class="flex flex-col gap-2 p-4">
          <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
        </div>
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">
          {{ $t('reports.avl.empty') }}
        </p>
        <template v-else>
          <UTable :data="rows" :columns="columns" />
          <div
            class="flex items-center justify-between gap-3 border-t border-default px-4 py-3 text-sm text-muted"
          >
            <span>{{
              $t(
                'reports.pagination.showing',
                { start: pageStart + 1, end: pageEnd, count: reportPage?.totalCount ?? 0 },
                reportPage?.totalCount ?? 0,
              )
            }}</span>
            <div class="flex gap-2">
              <UButton
                size="sm"
                color="neutral"
                variant="soft"
                :disabled="(reportPage?.page ?? 1) <= 1"
                @click="setPage((reportPage?.page ?? 1) - 1)"
              >
                {{ $t('reports.pagination.previous') }}
              </UButton>
              <UButton
                size="sm"
                color="neutral"
                variant="soft"
                :disabled="pageEnd >= (reportPage?.totalCount ?? 0)"
                @click="setPage((reportPage?.page ?? 1) + 1)"
              >
                {{ $t('reports.pagination.next') }}
              </UButton>
            </div>
          </div>
        </template>
      </section>
    </div>
  </AppPage>
</template>
