<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import VehicleList from '~/components/VehicleList.vue'
import VehicleFacets from '~/components/VehicleFacets.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useVehicleListPrefs } from '~/composables/useVehicleListPrefs'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { buildOverviewStats } from '~/utils/overviewStats'
import {
  VEHICLE_STATES,
  filterVehicles,
  type VehicleFilterState,
  type VehicleState,
} from '~/utils/vehicleFilters'

const { selectedAvlFeedCode, feedPath } = useFeeds()
const route = useRoute()
const router = useRouter()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { result: feedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

// State and routes live in the URL (?state=late&route=501,560) so a filtered view can be
// bookmarked and survives reloads; the search text stays local.
const query = ref('')
const knownStates = new Set<string>(VEHICLE_STATES.map((s) => s.value))

function queryList(value: unknown): string[] {
  const values = Array.isArray(value) ? value : [value]
  return values.flatMap((v) => (typeof v === 'string' ? v.split(',') : [])).filter(Boolean)
}

const filters = computed<VehicleFilterState>({
  get: () => {
    const state = typeof route.query.state === 'string' ? route.query.state : null
    return {
      query: query.value,
      state: state && knownStates.has(state) ? (state as VehicleState) : null,
      routeIds: queryList(route.query.route),
    }
  },
  set: (next) => {
    query.value = next.query
    const state = next.state ?? undefined
    const routeParam = next.routeIds.length ? next.routeIds.join(',') : undefined
    if (state === route.query.state && routeParam === route.query.route) return
    router.replace({ query: { ...route.query, state, route: routeParam } })
  },
})
const filtered = computed(() => filterVehicles(vehicles.value, filters.value))
const filtersOpen = ref(false)
const { showTripDetails } = useVehicleListPrefs()
const activeFilterCount = computed(
  () => (filters.value.state ? 1 : 0) + filters.value.routeIds.length,
)

const stats = computed(() => buildOverviewStats(vehicles.value, feedsResult.value?.avlFeeds ?? []))
const newestReport = computed(() =>
  vehicles.value.reduce<string | null>(
    (max, v) => (!max || v.reportTs > max ? v.reportTs : max),
    null,
  ),
)
const showSkeleton = computed(() => loading.value && vehicles.value.length === 0)

function openVehicle(vehicleId: string) {
  navigateTo(feedPath(`/vehicles/${vehicleId}`))
}
</script>

<template>
  <AppPage
    title="Vehicles"
    description="Live vehicle positions, route matching, and service status"
  >
    <template #actions>
      <NavbarActions :updated-at="newestReport" />
    </template>

    <template v-if="selectedAvlFeedCode" #toolbar>
      <div class="flex flex-wrap items-center justify-between gap-3 py-2">
        <div class="flex flex-wrap items-center gap-x-5 gap-y-1">
          <div v-for="stat in stats" :key="stat.label" class="flex items-center gap-1.5 text-sm">
            <UIcon :name="stat.icon" class="size-4 shrink-0 text-dimmed" />
            <span class="font-semibold text-highlighted">{{ stat.value }}</span>
            <span class="text-muted">{{ stat.label }}</span>
          </div>
        </div>
      </div>
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" what="the live vehicle map" />

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Vehicle feed unavailable"
      :description="error.message"
      class="m-4"
    />

    <div v-else class="flex min-h-0 flex-1 flex-col">
      <section class="app-frame flex min-h-0 flex-1 flex-col">
        <div
          class="flex flex-wrap items-center justify-between gap-3 border-b border-default bg-default/70 px-4 py-2.5"
        >
          <div>
            <div class="text-sm font-medium text-highlighted">Vehicle directory</div>
            <div class="text-xs text-muted">Current reports from the selected realtime feed</div>
          </div>
          <div class="flex flex-1 flex-wrap items-center justify-end gap-2">
            <UInput
              :model-value="filters.query"
              icon="i-lucide-search"
              placeholder="Search vehicle, route, trip or block"
              class="w-full sm:w-80"
              @update:model-value="filters = { ...filters, query: String($event) }"
            />
<USwitch
              v-model="showTripDetails"
              size="sm"
              label="Trip & block"
              class="shrink-0"
            />
            <UButton
              class="lg:hidden"
              color="neutral"
              variant="subtle"
              icon="i-lucide-sliders-horizontal"
              @click="filtersOpen = true"
            >
              Filters
              <UBadge v-if="activeFilterCount" size="sm" variant="soft">{{
                activeFilterCount
              }}</UBadge>
            </UButton>
            <span class="whitespace-nowrap text-xs text-dimmed"
              >{{ filtered.length.toLocaleString() }} of
              {{ vehicles.length.toLocaleString() }} vehicles</span
            >
          </div>
        </div>
        <div class="flex min-h-0 flex-1">
          <aside
            class="hidden w-60 shrink-0 overflow-y-auto border-r border-default p-3 lg:block"
            aria-label="Vehicle filters"
          >
            <VehicleFacets v-model="filters" :vehicles="vehicles" />
          </aside>
          <div class="min-h-0 min-w-0 flex-1 overflow-auto">
            <div v-if="showSkeleton" class="flex flex-col gap-2 p-3">
              <USkeleton v-for="i in 6" :key="i" class="h-10 w-full" />
            </div>
            <div
              v-else-if="filtered.length === 0"
              class="flex h-full flex-col items-center justify-center gap-2 p-8 text-center"
            >
              <UIcon name="i-lucide-search-x" class="size-7 text-dimmed" />
              <p class="text-sm font-medium text-highlighted">No vehicles match these filters</p>
              <UButton
                color="neutral"
                variant="soft"
                label="Show all vehicles"
                @click="filters = { query: '', state: null, routeIds: [] }"
              />
            </div>
            <VehicleList v-else :vehicles="filtered" @select="openVehicle" />
          </div>
        </div>
        <USlideover v-model:open="filtersOpen" side="left" title="Filter vehicles">
          <template #body>
            <VehicleFacets v-model="filters" :vehicles="vehicles" />
          </template>
        </USlideover>
      </section>
    </div>
  </AppPage>
</template>
