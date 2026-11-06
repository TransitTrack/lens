<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import {
  type TripAdherenceQuery,
  type TripsByRouteQuery,
  useExploreRoutesQuery,
  useTripAdherenceQuery,
  useTripsByRouteQuery,
} from '~~/generated/graphql'
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { adherenceBadge, signedAdherence } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'
import { occupancyLabel } from '~/utils/vehicleTelemetry'
import { formatHm } from '~/utils/tripFilters'

interface ReportFilters {
  routeId: string | null
  tripId: string | null
  startDate: string
  endDate: string
}

function today(): string {
  // Machine format: 'en-CA' yields ISO yyyy-mm-dd for the API and date inputs, so it must not follow the UI locale.
  const parts = new Intl.DateTimeFormat('en-CA').formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

const { t } = useI18n()
const { selectedFeedCode, selectedAvlFeedCode } = useFeeds()

// Deep-linked from the route-level adherence report (a "Trip" cell there
// links here with the same route, trip and date range already chosen).
const route = useRoute()
function queryString(key: string): string | null {
  const v = route.query[key]
  return typeof v === 'string' ? v : null
}

const filters = reactive<ReportFilters>({
  routeId: queryString('routeId'),
  tripId: queryString('tripId'),
  startDate: queryString('startDate') ?? today(),
  endDate: queryString('endDate') ?? today(),
})
const submitted = ref<ReportFilters | null>(null)

// The filters panel starts open and collapses once a report has run, so the
// results get the room back; re-opening is still one click away via the
// accordion header.
const FILTERS_ACCORDION_VALUE = 'filters'
const filtersAccordionItems = computed(() => [
  { label: t('reports.filters.title'), value: FILTERS_ACCORDION_VALUE },
])
const openFilterAccordion = ref<string | undefined>(FILTERS_ACCORDION_VALUE)

const {
  result: routesResult,
  loading: routesLoading,
  error: routesError,
} = useExploreRoutesQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value,
}))

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

// Clear the trip selection when the route changes — but a deep link arrives
// with both already set, so skip the first reset or it'd clobber the tripId
// before this watcher's even had a chance to read it.
let skipNextTripReset = !!filters.routeId && !!filters.tripId
watch(
  () => filters.routeId,
  () => {
    if (skipNextTripReset) {
      skipNextTripReset = false
      return
    }
    filters.tripId = null
  },
)

const { result: tripsResult, loading: tripsLoading } = useTripsByRouteQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '', routeId: filters.routeId ?? '' },
  enabled: !!selectedFeedCode.value && !!filters.routeId,
}))

type Trip = TripsByRouteQuery['trips'][number]

const tripItems = computed(() =>
  [...(tripsResult.value?.trips ?? [])]
    .sort((a: Trip, b: Trip) => (a.startTimeSec ?? Infinity) - (b.startTimeSec ?? Infinity))
    .map((trip: Trip) => ({
      label: `${trip.tripHeadsign ?? trip.tripShortName ?? trip.tripId}${trip.startTimeSec != null ? ` (${formatHm(trip.startTimeSec)})` : ''}`,
      value: trip.tripId,
    })),
)

const canRun = computed(
  () =>
    !!selectedAvlFeedCode.value &&
    !!filters.tripId &&
    isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value || !filters.tripId) return
  submitted.value = { ...filters }
  openFilterAccordion.value = undefined
}

// Auto-run once for a deep link, as soon as the AVL feed resolves and makes
// `canRun` true, so the report is already showing instead of making the
// visitor press "Run report" again for a trip they already chose.
if (filters.tripId) {
  if (canRun.value) {
    runReport()
  } else {
    const stopAutoRun = watch(canRun, (ok) => {
      if (!ok) return
      runReport()
      stopAutoRun()
    })
  }
}

const {
  result: adherenceResult,
  loading: adherenceLoading,
  error: adherenceError,
} = useTripAdherenceQuery(() => ({
  variables: { feedCode: selectedAvlFeedCode.value ?? '', tripId: submitted.value?.tripId ?? '', startDate: submitted.value?.startDate ?? '', endDate: submitted.value?.endDate ?? '' },
  enabled: !!selectedAvlFeedCode.value && !!submitted.value, fetchPolicy: 'network-only',
}))

type ReportRow = TripAdherenceQuery['tripAdherence'][number]

const rows = computed<ReportRow[]>(() =>
  [...(adherenceResult.value?.tripAdherence ?? [])].sort((a, b) => {
    if (a.serviceDate !== b.serviceDate) return a.serviceDate.localeCompare(b.serviceDate)
    return a.stopPathIndex - b.stopPathIndex
  }),
)

function adherenceTextClass(sec: number | null): string {
  switch (adherenceBadge(sec, t).color) {
    case 'error':
      return 'text-error'
    case 'warning':
      return 'text-warning'
    case 'success':
      return 'text-success'
    default:
      return 'text-muted'
  }
}

// Passenger count, Change and Bus filling aren't tracked anywhere in this
// system (no GTFS-RT vehicle occupancy_percentage/passenger-count support),
// and Departure is likewise unavailable - AVL pings carry one timestamp per
// ping, not a separate arrival/departure pair. These columns always render
// this placeholder rather than being silently omitted.
const NOT_TRACKED = computed(() => t('reports.notAvailable'))

const columns = computed<TableColumn<ReportRow>[]>(() => [
  {
    id: 'number',
    header: '#',
    cell: ({ row }) => row.index + 1,
  },
  // Not part of the reference layout, but kept: this report spans a date
  // *range*, so without it rows from different service dates would be
  // indistinguishable.
  { accessorKey: 'serviceDate', header: t('reports.col.serviceDate') },
  {
    accessorKey: 'vehicleId',
    header: t('vehicles.field.vehicle'),
    cell: ({ row }) => h('strong', { class: 'font-medium' }, row.original.vehicleId),
  },
  {
    accessorKey: 'stopId',
    header: t('explore.col.stop'),
    cell: ({ row }) =>
      row.original.stopName
        ? t('reports.col.stopWithId', { name: row.original.stopName, id: row.original.stopId })
        : row.original.stopId,
  },
  {
    accessorKey: 'observedSec',
    header: t('reports.col.arrival'),
    cell: ({ row }) => formatHm(row.original.observedSec),
  },
  {
    id: 'departure',
    header: t('reports.col.departure'),
    cell: () => NOT_TRACKED.value,
  },
  {
    accessorKey: 'scheduledSec',
    header: t('reports.col.schedule'),
    cell: ({ row }) => formatHm(row.original.scheduledSec),
  },
  {
    id: 'deviation',
    header: t('vehicles.avl.column.deviation'),
    cell: ({ row }) => {
      // Here, `null` means "this stop was scheduled but never observed in the AVL data" (a
      // reporting gap), not "not on a scheduled trip" - `adherenceBadge`'s live-tracking
      // "Unscheduled" label would be misleading next to a real Scheduled-time value.
      const sec = row.original.adherenceSec
      if (sec == null) {
        return h('span', { class: 'text-muted' }, t('reports.adherence.notObserved'))
      }
      return h(
        'span',
        {
          class: [
            'rounded px-1.5 py-0.5 text-xs font-semibold tabular-nums bg-current/10',
            adherenceTextClass(sec),
          ],
        },
        signedAdherence(sec),
      )
    },
  },
  {
    accessorKey: 'direction',
    header: t('vehicles.detail.fact.direction'),
    cell: ({ row }) => row.original.direction ?? '—',
  },
  {
    id: 'passengerCount',
    header: t('reports.col.passengerCount'),
    cell: () => NOT_TRACKED.value,
  },
  {
    id: 'change',
    header: t('reports.col.change'),
    cell: () => NOT_TRACKED.value,
  },
  {
    id: 'busFilling',
    header: t('reports.col.busFilling'),
    cell: () => NOT_TRACKED.value,
  },
  {
    accessorKey: 'occupancyStatus',
    header: t('reports.col.occupancyStatus'),
    cell: ({ row }) => row.original.occupancyStatus ? occupancyLabel(row.original.occupancyStatus, t) : '—',
  },
])
</script>

<template>
  <AppPage
    :title="$t('reports.trip.title')"
    :description="$t('reports.trip.description')"
  >
    <template #actions>
      <NavbarActions />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" :what="$t('reports.noRealtime.adherence')" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 py-4">
      <div class="app-frame bg-elevated/20 px-4">
        <UAccordion
          v-model="openFilterAccordion"
          type="single"
          collapsible
          :items="filtersAccordionItems"
        >
          <template #trailing>
            <span v-if="routesLoading" class="text-sm text-dimmed">{{ $t('reports.filters.loadingRoutes') }}</span>
            <UIcon
              name="i-lucide-chevron-down"
              class="shrink-0 size-5 ms-auto group-data-[state=open]:rotate-180 transition-transform duration-200 ease-out motion-reduce:transition-none"
            />
          </template>
          <template #body>
            <UAlert
              v-if="routesError"
              color="error"
              variant="soft"
              icon="i-lucide-alert-triangle"
              :title="$t('reports.error.routes')"
              :description="routesError.message"
            />

            <div
              v-else
              class="grid gap-3 md:grid-cols-[minmax(16rem,1fr)_minmax(16rem,1fr)_auto_auto] md:items-end"
            >
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('explore.col.route') }}
                <USelectMenu
                  v-model="filters.routeId"
                  data-testid="report-route"
                  :items="routeItems"
                  value-key="value"
                  searchable
                  :placeholder="$t('reports.filters.selectRoute')"
                  icon="i-lucide-route"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('explore.col.trip') }}
                <USelectMenu
                  v-model="filters.tripId"
                  data-testid="report-trip"
                  :items="tripItems"
                  value-key="value"
                  searchable
                  :disabled="!filters.routeId"
                  :loading="tripsLoading"
                  :placeholder="$t('reports.filters.selectTrip')"
                  icon="i-lucide-git-branch"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.startDate') }}
                <UInput v-model="filters.startDate" type="date" data-testid="report-start-date" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.endDate') }}
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
                {{ $t('reports.filters.endBeforeStart') }}
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
          v-if="adherenceError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          :title="$t('reports.error.adherence')"
          :description="adherenceError.message"
          class="m-4"
        />
        <div v-else-if="adherenceLoading && !rows.length" class="flex flex-col gap-2 p-4">
          <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
        </div>
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">
          {{ $t('reports.trip.empty') }}
        </p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
