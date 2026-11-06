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

const { t } = useI18n()
const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()
const { param } = useExploreQuery()

const routeId = param('route')
const dir = param('dir')
const from = param('from')
const to = param('to')

const { result: routesResult } = useExploreRoutesQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value,
}))
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

const { result, loading } = useTripsByRouteQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '', routeId: routeId.value ?? '' },
  enabled: !!selectedFeedCode.value && !!routeId.value,
}))

type Trip = TripsByRouteQuery['trips'][number]

const directionItems = computed(() => {
  const set = new Set<number>()
  for (const t of result.value?.trips ?? []) if (t.directionId != null) set.add(t.directionId)
  return [
    { label: t('explore.trips.bothDirections'), value: null },
    ...[...set].sort((a, b) => a - b).map((d) => ({ label: t('explore.trips.direction', { dir: d }), value: String(d) })),
  ]
})

const { onlyWithFindings, count: findingsCount, keep: hasFindings } = useFindingsFilter(
  selectedFeedCode,
  'Trip',
)
const search = ref('')
const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  const fromSec = parseHm(from.value)
  const toSec = parseHm(to.value)
  return [...(result.value?.trips ?? [])]
    .filter((t) => {
      if (dir.value != null && String(t.directionId) !== dir.value) return false
      if (!inWindow(t.startTimeSec, fromSec, toSec)) return false
      if (!hasFindings(t.tripId)) return false
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

const columns = computed<TableColumn<Trip>[]>(() => [
  { accessorKey: 'tripId', header: t('explore.col.trip') },
  { accessorKey: 'tripHeadsign', header: t('explore.col.headsign') },
  { accessorKey: 'directionId', header: t('explore.col.dir') },
  { accessorKey: 'startTimeSec', header: t('explore.col.start') },
  { accessorKey: 'endTimeSec', header: t('explore.col.end') },
  { accessorKey: 'serviceId', header: t('explore.col.service') },
])

function onSelect(_e: Event, row: { original: Trip }) {
  navigateTo(feedPath(`/explore/trips/${row.original.tripId}`))
}
</script>

<template>
  <AppPage
    :title="t('nav.trips')"
    class="scroll"
    :description="t('explore.trips.description')"
  >
    <template #toolbar>
      <ExploreToolbar />
    </template>

    <section class="app-frame">
      <div class="flex flex-wrap items-center gap-2 p-3">
        <USelectMenu
          :model-value="routeId"
          :items="routeItems"
          value-key="value"
          :placeholder="t('explore.trips.pickRoute')"
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
          <UInput
            v-model="search"
            icon="i-lucide-search"
            :placeholder="t('explore.trips.filterPlaceholder')"
            class="w-72"
          />
          <USwitch
            v-if="findingsCount > 0 || onlyWithFindings"
            v-model="onlyWithFindings"
            size="sm"
            :label="t('findings.filterWith', { count: findingsCount })"
          />
        </template>
      </div>

      <div v-if="routeId" class="border-t border-default px-3 py-2">
        <TripTimeFilter />
      </div>
    </section>

    <div
      v-if="!routeId"
      class="mx-auto flex max-w-md flex-col items-center gap-3 py-20 text-center"
    >
      <div class="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        <UIcon name="i-lucide-route" class="size-6" />
      </div>
      <div class="text-base font-medium text-highlighted">
        {{ t('explore.trips.choose') }}
      </div>
      <p class="text-sm text-muted">
        {{ t('explore.trips.chooseBody') }}
      </p>
    </div>

    <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
    </div>

    <template v-else>
      <section class="app-frame grid sm:grid-cols-2 xl:grid-cols-4">
        <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{
            t('explore.trips.visibleTrips')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.trips
          }}</span>
          <span class="text-xs text-muted">{{
            selectedRoute?.routeShortName ?? selectedRoute?.routeId
          }}</span>
        </div>
        <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{
            t('explore.trips.directions')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.directions
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.trips.inSelectedView') }}</span>
        </div>
        <div
          class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r"
        >
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >{{
            t('explore.trips.serviceCalendars')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.services
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.trips.distinctServiceIds') }}</span>
        </div>
        <div class="flex flex-col gap-1 p-4">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >{{
            t('explore.trips.operatingSpan')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            tripStats.span
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.trips.firstToLast') }}</span>
        </div>
      </section>

      <section class="app-frame">
        <div class="flex items-center justify-between border-b border-default px-4 py-3">
          <div>
            <div class="text-sm font-medium text-highlighted">
              {{ t('explore.trips.journeys') }}
            </div>
            <div class="text-xs text-muted">{{ t('explore.trips.selectTrip') }}</div>
          </div>
          <span class="text-xs text-dimmed">{{
            t('feed.overview.trips', { count: rows.length }, rows.length)
          }}</span>
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
