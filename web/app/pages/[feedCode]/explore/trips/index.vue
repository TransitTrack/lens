<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import TripTimeFilter from '~/components/TripTimeFilter.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import { useExploreQuery } from '~/composables/useExploreQuery'
import {
  useExploreRoutesQuery,
  useTripsByRouteQuery,
  type TripsByRouteQuery,
} from '~~/generated/graphql'
import { formatHm, parseHm, inWindow } from '~/utils/tripFilters'

const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()
const { param } = useExploreQuery()

const routeId = param('route')
const dir = param('dir')
const from = param('from')
const to = param('to')

const { result: routesResult } = useExploreRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const routeItems = computed(() =>
  [...(routesResult.value?.routes ?? [])]
    .filter((r) => !agencyId.value || r.agencyId === agencyId.value)
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

// drop the picked route if it no longer belongs to the selected agency
watch(agencyId, () => {
  if (routeId.value && !routeItems.value.some((r) => r.value === routeId.value)) {
    routeId.value = null
  }
})

const { result, loading } = useTripsByRouteQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '', routeId: routeId.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value && !!routeId.value }),
)

type Trip = TripsByRouteQuery['trips'][number]

const directionItems = computed(() => {
  const set = new Set<number>()
  for (const t of result.value?.trips ?? []) if (t.directionId != null) set.add(t.directionId)
  return [
    { label: 'Both directions', value: null },
    ...[...set].sort((a, b) => a - b).map((d) => ({ label: `Direction ${d}`, value: String(d) })),
  ]
})

const search = ref('')
const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  const fromSec = parseHm(from.value)
  const toSec = parseHm(to.value)
  return [...(result.value?.trips ?? [])]
    .filter((t) => {
      if (dir.value != null && String(t.directionId) !== dir.value) return false
      if (!inWindow(t.startTimeSec, fromSec, toSec)) return false
      if (
        q &&
        ![t.tripHeadsign, t.tripShortName, t.tripId, t.serviceId].some((v) =>
          v?.toLowerCase().includes(q),
        )
      ) {
        return false
      }
      return true
    })
    .sort((a, b) => (a.startTimeSec ?? Infinity) - (b.startTimeSec ?? Infinity))
})

const selectedRoute = computed(
  () => (routesResult.value?.routes ?? []).find((route) => route.routeId === routeId.value) ?? null,
)

const tripStats = computed(() => {
  const trips = rows.value
  const startTimes = trips
    .map((trip) => trip.startTimeSec)
    .filter((time): time is number => time != null)
  return {
    trips: trips.length,
    directions: new Set(
      trips.map((trip) => trip.directionId).filter((direction) => direction != null),
    ).size,
    services: new Set(trips.map((trip) => trip.serviceId).filter(Boolean)).size,
    span: startTimes.length
      ? `${formatHm(Math.min(...startTimes))}–${formatHm(Math.max(...startTimes))}`
      : '—',
  }
})

const columns: TableColumn<Trip>[] = [
  { accessorKey: 'tripId', header: 'Trip' },
  { accessorKey: 'tripHeadsign', header: 'Headsign' },
  { accessorKey: 'directionId', header: 'Dir' },
  { accessorKey: 'startTimeSec', header: 'Start' },
  { accessorKey: 'endTimeSec', header: 'End' },
  { accessorKey: 'serviceId', header: 'Service' },
]

function onSelect(_e: Event, row: { original: Trip }) {
  navigateTo(feedPath(`/explore/trips/${row.original.tripId}`))
}
</script>

<template>
  <AppPage
    title="Trips"
    description="Scheduled journeys, service coverage, and stop-level timetable detail"
  >
    <template #toolbar>
      <ExploreToolbar />
    </template>

    <section class="flex flex-wrap items-center gap-2 border border-default bg-default p-3">
      <USelectMenu
        :model-value="routeId"
        :items="routeItems"
        value-key="value"
        placeholder="Pick a route"
        icon="i-lucide-route"
        class="w-72"
        @update:model-value="routeId = $event"
      />
      <template v-if="routeId">
        <USelectMenu
          :model-value="dir"
          :items="directionItems"
          value-key="value"
          class="w-72"
          @update:model-value="dir = $event"
        />
        <UInput v-model="search" icon="i-lucide-search" placeholder="Filter trips…" class="w-72" />
      </template>
    </section>

    <TripTimeFilter v-if="routeId" />

    <div
      v-if="!routeId"
      class="mx-auto flex max-w-md flex-col items-center gap-3 py-20 text-center"
    >
      <div class="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <UIcon name="i-lucide-route" class="size-6" />
      </div>
      <div class="text-base font-medium text-highlighted">Choose a route to analyse</div>
      <p class="text-sm text-muted">
        Review scheduled journeys, operating span, service calendars, and stop-level timetable
        detail.
      </p>
    </div>

    <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
    </div>

    <template v-else>
      <section
        class="grid overflow-hidden border border-default bg-default sm:grid-cols-2 xl:grid-cols-4"
      >
        <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Visible trips</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.trips
          }}</span>
          <span class="text-xs text-muted">{{
            selectedRoute?.routeShortName ?? selectedRoute?.routeId
          }}</span>
        </div>
        <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Directions</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.directions
          }}</span>
          <span class="text-xs text-muted">In the selected view</span>
        </div>
        <div
          class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r"
        >
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >Service calendars</span
          >
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.services
          }}</span>
          <span class="text-xs text-muted">Distinct service IDs</span>
        </div>
        <div class="flex flex-col gap-1 p-4">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >Operating span</span
          >
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.span
          }}</span>
          <span class="text-xs text-muted">First to last departure</span>
        </div>
      </section>

      <section class="overflow-hidden border border-default bg-default">
        <div class="flex items-center justify-between border-b border-default px-4 py-3">
          <div>
            <div class="text-sm font-medium text-highlighted">Scheduled journeys</div>
            <div class="text-xs text-muted">Select a trip to inspect its timetable and shape.</div>
          </div>
          <span class="text-xs text-dimmed">{{ rows.length }} trips</span>
        </div>
        <UTable :data="rows" :columns="columns" @select="onSelect">
          <template #directionId-cell="{ row }">{{ row.original.directionId ?? '—' }}</template>
          <template #startTimeSec-cell="{ row }">{{
            formatHm(row.original.startTimeSec)
          }}</template>
          <template #endTimeSec-cell="{ row }">{{ formatHm(row.original.endTimeSec) }}</template>
        </UTable>
      </section>
    </template>
  </AppPage>
</template>
