<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import NavbarActions from '~/components/NavbarActions.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import {
  useExploreRoutesQuery,
  useTripsByRouteQuery,
  type TripsByRouteQuery,
} from '~~/generated/graphql'

const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()

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
    .map((r) => ({ label: `${r.routeShortName ?? r.routeId} — ${r.routeLongName ?? ''}`, value: r.routeId })),
)

const routeId = ref<string | null>(null)

// clear the picked route if it no longer belongs to the selected agency
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
const search = ref('')
const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = result.value?.trips ?? []
  if (!q) return list
  return list.filter((t) =>
    [t.tripHeadsign, t.tripShortName, t.tripId, t.serviceId].some((v) => v?.toLowerCase().includes(q)),
  )
})

const columns: TableColumn<Trip>[] = [
  { accessorKey: 'tripId', header: 'Trip' },
  { accessorKey: 'tripHeadsign', header: 'Headsign' },
  { accessorKey: 'directionId', header: 'Dir' },
  { accessorKey: 'serviceId', header: 'Service' },
]

function onSelect(_e: Event, row: { original: Trip }) {
  navigateTo(feedPath(`/explore/trips/${row.original.tripId}`))
}
</script>

<template>
  <UDashboardPanel id="explore-trips">
    <template #header>
      <UDashboardNavbar title="Trips">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <NavbarActions />
        </template>
      </UDashboardNavbar>
      <ExploreToolbar />
    </template>

    <template #body>
      <div class="flex flex-wrap items-center gap-2">
        <USelectMenu
          v-model="routeId"
          :items="routeItems"
          value-key="value"
          placeholder="Pick a route"
          icon="i-lucide-route"
          class="w-72"
        />
        <UInput
          v-if="routeId"
          v-model="search"
          icon="i-lucide-search"
          placeholder="Filter trips…"
          class="max-w-xs"
        />
      </div>

      <p v-if="!routeId" class="text-sm text-muted">Pick a route to list its trips.</p>

      <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
        <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
      </div>

      <template v-else>
        <div class="text-xs text-dimmed">{{ rows.length }} trips</div>
        <UTable :data="rows" :columns="columns" @select="onSelect">
          <template #directionId-cell="{ row }">{{ row.original.directionId ?? '—' }}</template>
        </UTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
