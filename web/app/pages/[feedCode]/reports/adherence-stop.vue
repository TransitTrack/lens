<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import {
  type StopAdherenceQuery,
  useExploreStopsQuery,
  useStopAdherenceQuery,
} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { adherenceBadge } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'
import { formatHm } from '~/utils/tripFilters'

interface ReportFilters {
  stopId: string | null
  startDate: string
  endDate: string
}

function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA').formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

const { selectedFeedCode, selectedAvlFeedCode } = useFeeds()

const filters = reactive<ReportFilters>({
  stopId: null,
  startDate: today(),
  endDate: today(),
})
const submitted = ref<ReportFilters | null>(null)

// The filters panel starts open and collapses once a report has run, so the
// results get the room back; re-opening is still one click away via the
// accordion header.
const FILTERS_ACCORDION_VALUE = 'filters'
const filtersAccordionItems = [{ label: 'Report filters', value: FILTERS_ACCORDION_VALUE }]
const openFilterAccordion = ref<string | undefined>(FILTERS_ACCORDION_VALUE)

const {
  result: stopsResult,
  loading: stopsLoading,
  error: stopsError,
} = useExploreStopsQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const stopItems = computed(() =>
  [...(stopsResult.value?.stops ?? [])]
    .sort((a, b) =>
      (a.stopName ?? a.stopId).localeCompare(b.stopName ?? b.stopId, undefined, { numeric: true }),
    )
    .map((stop) => ({
      label: `${stop.stopName ?? stop.stopId} (${stop.stopCode ?? stop.stopId})`,
      value: stop.stopId,
    })),
)

const canRun = computed(
  () =>
    !!selectedAvlFeedCode.value &&
    !!filters.stopId &&
    isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value || !filters.stopId) return
  submitted.value = { ...filters }
  openFilterAccordion.value = undefined
}

const {
  result: adherenceResult,
  loading: adherenceLoading,
  error: adherenceError,
} = useStopAdherenceQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    stopId: submitted.value?.stopId ?? '',
    startDate: submitted.value?.startDate ?? '',
    endDate: submitted.value?.endDate ?? '',
  }),
  () => ({
    enabled: !!selectedAvlFeedCode.value && !!submitted.value,
    fetchPolicy: 'network-only',
  }),
)

type ReportRow = StopAdherenceQuery['stopAdherence'][number]

const rows = computed<ReportRow[]>(() =>
  [...(adherenceResult.value?.stopAdherence ?? [])].sort((a, b) => {
    if (a.serviceDate !== b.serviceDate) return a.serviceDate.localeCompare(b.serviceDate)
    return (a.scheduledSec ?? 0) - (b.scheduledSec ?? 0)
  }),
)

const UBadge = resolveComponent('UBadge')

const columns: TableColumn<ReportRow>[] = [
  { accessorKey: 'tripId', header: 'Trip' },
  { accessorKey: 'routeId', header: 'Route' },
  { accessorKey: 'serviceDate', header: 'Service date' },
  {
    accessorKey: 'scheduledSec',
    header: 'Scheduled time',
    cell: ({ row }) => formatHm(row.original.scheduledSec),
  },
  {
    accessorKey: 'observedSec',
    header: 'Observed time',
    cell: ({ row }) => formatHm(row.original.observedSec),
  },
  {
    id: 'adherence',
    header: 'Adherence',
    cell: ({ row }) => {
      // Here, `null` means "this stop was scheduled but never observed in the AVL data" (a
      // reporting gap), not "not on a scheduled trip" - `adherenceBadge`'s live-tracking
      // "Unscheduled" label would be misleading next to a real Scheduled-time value.
      if (row.original.adherenceSec == null) {
        return h(UBadge, { color: 'neutral', variant: 'subtle' }, { default: () => 'Not observed' })
      }
      const badge = adherenceBadge(row.original.adherenceSec)
      return h(UBadge, { color: badge.color, variant: 'subtle' }, { default: () => badge.label })
    },
  },
]
</script>

<template>
  <AppPage
    title="Schedule Adherence for a Stop"
    description="Per-visit adherence for one stop across a date range"
  >
    <template #actions>
      <NavbarActions />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="schedule adherence reports" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 py-4">
      <div class="rounded border border-default bg-elevated/20 px-4">
        <UAccordion
          v-model="openFilterAccordion"
          type="single"
          collapsible
          :items="filtersAccordionItems"
        >
          <template #trailing>
            <span v-if="stopsLoading" class="text-sm text-dimmed">Loading stops…</span>
            <UIcon
              name="i-lucide-chevron-down"
              class="shrink-0 size-5 ms-auto group-data-[state=open]:rotate-180 transition-transform duration-200 ease-out motion-reduce:transition-none"
            />
          </template>
          <template #body>
            <UAlert
              v-if="stopsError"
              color="error"
              variant="soft"
              icon="i-lucide-alert-triangle"
              title="Couldn't load stops"
              :description="stopsError.message"
            />

            <div v-else class="grid gap-3 md:grid-cols-[minmax(16rem,1fr)_auto_auto] md:items-end">
              <label class="grid gap-1 text-sm font-medium text-muted">
                Stop
                <USelectMenu
                  v-model="filters.stopId"
                  data-testid="report-stop"
                  :items="stopItems"
                  value-key="value"
                  searchable
                  placeholder="Select stop"
                  icon="i-lucide-map-pin"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                Start date
                <UInput v-model="filters.startDate" type="date" data-testid="report-start-date" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                End date
                <UInput v-model="filters.endDate" type="date" data-testid="report-end-date" />
              </label>
            </div>

            <div class="mt-4 flex items-center justify-between gap-3">
              <p
                v-if="
                  filters.startDate &&
                  filters.endDate &&
                  !isValidAdherenceRange(filters.startDate, filters.endDate)
                "
                class="text-sm text-error"
              >
                End date must be on or after the start date.
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
          v-if="adherenceError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          title="Couldn't load schedule adherence"
          :description="adherenceError.message"
          class="m-4"
        />
        <div v-else-if="adherenceLoading && !rows.length" class="flex flex-col gap-2 p-4">
          <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
        </div>
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">
          No schedule adherence data for this stop and date range.
        </p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
