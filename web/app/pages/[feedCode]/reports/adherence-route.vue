<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import {
  type RouteAdherenceQuery,
  useExploreRoutesQuery,
  useRouteAdherenceQuery,
} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { adherenceBadge } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'
import { formatHm } from '~/utils/tripFilters'

interface ReportFilters {
  routeId: string | null
  startDate: string
  endDate: string
}

function today(): string {
  const parts = new Intl.DateTimeFormat('en-CA').formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

const { selectedFeedCode, selectedAvlFeedCode } = useFeeds()

const filters = reactive<ReportFilters>({
  routeId: null,
  startDate: today(),
  endDate: today(),
})
const submitted = ref<ReportFilters | null>(null)

const { result: routesResult, loading: routesLoading, error: routesError } = useExploreRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const routeItems = computed(() =>
  [...(routesResult.value?.routes ?? [])]
    .sort((a, b) =>
      (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
        numeric: true,
      }),
    )
    .map((r) => ({
      label: `${r.routeShortName ?? r.routeId} — ${r.routeLongName ?? ''}`,
      value: r.routeId,
    })),
)

const canRun = computed(
  () =>
    !!selectedAvlFeedCode.value &&
    !!filters.routeId &&
    isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value || !filters.routeId) return
  submitted.value = { ...filters }
}

const {
  result: adherenceResult,
  loading: adherenceLoading,
  error: adherenceError,
} = useRouteAdherenceQuery(
  () => ({
    feedCode: selectedAvlFeedCode.value ?? '',
    routeId: submitted.value?.routeId ?? '',
    startDate: submitted.value?.startDate ?? '',
    endDate: submitted.value?.endDate ?? '',
  }),
  () => ({
    enabled: !!selectedAvlFeedCode.value && !!submitted.value,
    fetchPolicy: 'network-only',
  }),
)

type ReportRow = RouteAdherenceQuery['routeAdherence'][number]

const rows = computed<ReportRow[]>(() => adherenceResult.value?.routeAdherence ?? [])

const UBadge = resolveComponent('UBadge')

const columns: TableColumn<ReportRow>[] = [
  { accessorKey: 'tripId', header: 'Trip' },
  { accessorKey: 'serviceDate', header: 'Service date' },
  { accessorKey: 'stopId', header: 'Stop' },
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
    title="Schedule Adherence for a Route"
    description="Per-stop adherence for every trip on a route over a date range"
  >
    <template #actions>
      <NavbarActions />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="schedule adherence reports" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 p-4">
      <section class="rounded-lg border border-default bg-elevated/20 p-4">
        <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 class="font-semibold text-highlighted">Report filters</h2>
          </div>
          <span v-if="routesLoading" class="text-sm text-dimmed">Loading routes…</span>
        </div>

        <UAlert
          v-if="routesError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          title="Couldn't load routes"
          :description="routesError.message"
        />

        <div v-else class="grid gap-3 md:grid-cols-[minmax(16rem,1fr)_auto_auto] md:items-end">
          <label class="grid gap-1 text-sm font-medium text-muted">
            Route
            <USelectMenu
              v-model="filters.routeId"
              data-testid="report-route"
              :items="routeItems"
              value-key="value"
              searchable
              placeholder="Select route"
              icon="i-lucide-route"
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
            v-if="filters.startDate && filters.endDate && !isValidAdherenceRange(filters.startDate, filters.endDate)"
            class="text-sm text-error"
          >
            End date must be on or after the start date.
          </p>
          <span v-else />
          <UButton data-testid="report-run" icon="i-lucide-play" :disabled="!canRun" @click="runReport">
            Run report
          </UButton>
        </div>
      </section>

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
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">No schedule adherence data for this route and date range.</p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
