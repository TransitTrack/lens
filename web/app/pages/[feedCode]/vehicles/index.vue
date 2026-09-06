<script setup lang="ts">
import VehicleMap from '~/components/VehicleMap.vue'
import VehicleList from '~/components/VehicleList.vue'
import VehicleFilters from '~/components/VehicleFilters.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { buildOverviewStats } from '~/utils/overviewStats'
import { emptyFilters, filterVehicles } from '~/utils/vehicleFilters'

const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { extent } = useFeedExtent(selectedFeedCode)
const { result: feedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

const filters = ref(emptyFilters())
const filtered = computed(() => filterVehicles(vehicles.value, filters.value))
const isFiltered = computed(() => filtered.value.length !== vehicles.value.length)

const stats = computed(() =>
  buildOverviewStats(vehicles.value, feedsResult.value?.avlFeeds ?? []),
)
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
  <UDashboardPanel id="vehicles" :ui="{ body: 'p-0 sm:p-0 gap-0' }">
    <template #header>
      <UDashboardNavbar title="Vehicles" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <NavbarActions :updated-at="newestReport" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="selectedAvlFeedCode">
        <template #left>
          <div class="flex flex-wrap items-center gap-x-5 gap-y-1">
            <div v-for="stat in stats" :key="stat.label" class="flex items-center gap-1.5 text-sm">
              <UIcon :name="stat.icon" class="size-4 shrink-0 text-dimmed" />
              <span class="font-semibold text-highlighted">{{ stat.value }}</span>
              <span class="text-muted">{{ stat.label }}</span>
            </div>
          </div>
        </template>
        <template #right>
          <VehicleFilters v-model="filters" :feed-code="selectedFeedCode" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
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
        <div class="relative min-h-0 flex-1">
          <VehicleMap :vehicles="filtered" :extent="extent" @select="openVehicle" />
          <div
            v-if="showSkeleton"
            class="absolute left-1/2 top-4 -translate-x-1/2 rounded-md bg-default/80 px-3 py-1.5 text-sm text-muted backdrop-blur"
          >
            Loading vehicles…
          </div>
          <div
            v-else-if="isFiltered"
            class="absolute left-1/2 top-4 -translate-x-1/2 rounded-full bg-default/85 px-3 py-1 text-xs text-muted shadow ring ring-default backdrop-blur"
          >
            {{ filtered.length }} of {{ vehicles.length }} vehicles
          </div>
        </div>

        <div class="h-[40vh] shrink-0 overflow-y-auto border-t border-default">
          <div v-if="showSkeleton" class="flex flex-col gap-2 p-3">
            <USkeleton v-for="i in 6" :key="i" class="h-10 w-full" />
          </div>
          <VehicleList v-else :vehicles="filtered" @select="openVehicle" />
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
