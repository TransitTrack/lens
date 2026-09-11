<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import TripTimeFilter from '~/components/TripTimeFilter.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useAgencyFilter} from '~/composables/useAgencyFilter'
import {useExploreQuery} from '~/composables/useExploreQuery'
import {
  useExploreRoutesQuery,
  useTripsByRouteQuery,
  type TripsByRouteQuery,
} from '~~/generated/graphql'
import {formatHm, parseHm, inWindow} from '~/utils/tripFilters'

const {selectedFeedCode, feedPath} = useFeeds()
const {agencyId} = useAgencyFilter()
const {param} = useExploreQuery()

const routeId = param('route')
const dir = param('dir')
const from = param('from')
const to = param('to')

const {result: routesResult} = useExploreRoutesQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
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

const {result, loading} = useTripsByRouteQuery(
  () => ({feedCode: selectedFeedCode.value ?? '', routeId: routeId.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value && !!routeId.value}),
)

type Trip = TripsByRouteQuery['trips'][number]

const directionItems = computed(() => {
  const set = new Set<number>()
  for (const t of result.value?.trips ?? []) if (t.directionId != null) set.add(t.directionId)
  return [
    {label: 'Both directions', value: null},
    ...[...set].sort((a, b) => a - b).map((d) => ({label: `Direction ${d}`, value: String(d)})),
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

const columns: TableColumn<Trip>[] = [
  {accessorKey: 'tripId', header: 'Trip'},
  {accessorKey: 'tripHeadsign', header: 'Headsign'},
  {accessorKey: 'directionId', header: 'Dir'},
  {accessorKey: 'startTimeSec', header: 'Start'},
  {accessorKey: 'endTimeSec', header: 'End'},
  {accessorKey: 'serviceId', header: 'Service'},
]

function onSelect(_e: Event, row: { original: Trip }) {
  navigateTo(feedPath(`/explore/trips/${row.original.tripId}`))
}
</script>

<template>
  <AppPage title="Trips">
    <template #toolbar>
      <ExploreToolbar/>
    </template>

    <div class="flex flex-wrap items-center gap-2">
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
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Filter trips…"
          class="w-72"
        />
      </template>
    </div>

    <TripTimeFilter v-if="routeId"/>

    <p v-if="!routeId" class="text-sm text-muted">Pick a route to list its trips.</p>

    <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 8" :key="i" class="h-10 w-full"/>
    </div>

    <template v-else>
      <div class="text-xs text-dimmed">{{ rows.length }} trips</div>
      <UTable :data="rows" :columns="columns" @select="onSelect">
        <template #directionId-cell="{ row }">{{ row.original.directionId ?? '—' }}</template>
        <template #startTimeSec-cell="{ row }">{{ formatHm(row.original.startTimeSec) }}</template>
        <template #endTimeSec-cell="{ row }">{{ formatHm(row.original.endTimeSec) }}</template>
      </UTable>
    </template>
  </AppPage>
</template>
