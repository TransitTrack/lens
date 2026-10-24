<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import { type AllRoutesAdherenceQuery, useAllRoutesAdherenceQuery } from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { formatAdherence } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'

interface ReportFilters {
  startDate: string
  endDate: string
}

function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA').formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

const { selectedAvlFeedCode } = useFeeds()

const filters = reactive<ReportFilters>({
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

const canRun = computed(
  () => !!selectedAvlFeedCode.value && isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value) return
  submitted.value = { ...filters }
  openFilterAccordion.value = undefined
}

const {
  result: adherenceResult,
  loading: adherenceLoading,
  error: adherenceError,
} = useAllRoutesAdherenceQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    startDate: submitted.value?.startDate ?? '',
    endDate: submitted.value?.endDate ?? '',
  }),
  () => ({
    enabled: !!selectedAvlFeedCode.value && !!submitted.value,
    fetchPolicy: 'network-only',
  }),
)

type ReportRow = AllRoutesAdherenceQuery['allRoutesAdherence'][number]

const rows = computed<ReportRow[]>(() =>
  [...(adherenceResult.value?.allRoutesAdherence ?? [])].sort((a, b) =>
    a.routeId.localeCompare(b.routeId, undefined, { numeric: true }),
  ),
)

const columns: TableColumn<ReportRow>[] = [
  {
    id: 'route',
    header: 'Route',
    cell: ({ row }) => row.original.routeShortName ?? row.original.routeId,
  },
  { accessorKey: 'sampleCount', header: 'Sample count' },
  {
    id: 'meanAdherence',
    header: 'Mean adherence',
    cell: ({ row }) => formatAdherence(Math.round(row.original.meanAdherenceSec)),
  },
  {
    id: 'onTimePct',
    header: 'On-time %',
    cell: ({ row }) => `${row.original.onTimePct.toFixed(1)}%`,
  },
]
</script>

<template>
  <AppPage
    title="Schedule Adherence for all Routes"
    description="Mean adherence and on-time rate per route over a date range"
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
          <template #body>
            <div class="grid gap-3 md:grid-cols-[auto_auto] md:items-end">
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
          No schedule adherence data for this date range.
        </p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
