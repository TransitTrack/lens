<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import VehicleList from '~/components/VehicleList.vue'
import VehicleFilters from '~/components/VehicleFilters.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useVehiclePolling } from '~/composables/useVehiclePolling'
import { useAvlFeedsQuery } from '~~/generated/graphql'
import { buildOverviewStats } from '~/utils/overviewStats'
import {
  emptyFilters,
  filterVehicles,
  STATUS_OPTIONS,
  type VehicleStatus,
} from '~/utils/vehicleFilters'

const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const route = useRoute()
const { vehicles, loading, error } = useVehiclePolling(selectedAvlFeedCode)
const { result: feedsResult } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

const filters = ref(emptyFilters())
const filtered = computed(() => filterVehicles(vehicles.value, filters.value))

const stats = computed(() => buildOverviewStats(vehicles.value, feedsResult.value?.avlFeeds ?? []))
const newestReport = computed(() =>
  vehicles.value.reduce<string | null>(
    (max, v) => (!max || v.reportTs > max ? v.reportTs : max),
    null,
  ),
)
const showSkeleton = computed(() => loading.value && vehicles.value.length === 0)

const vehicleStatuses = new Set(STATUS_OPTIONS.map((option) => option.value))

function queryStatuses(value: unknown): VehicleStatus[] {
  const values = Array.isArray(value) ? value : [value]
  return values
    .flatMap((status) => String(status ?? '').split(','))
    .filter((status): status is VehicleStatus => vehicleStatuses.has(status as VehicleStatus))
}

watch(
  () => route.query.status,
  (status) => {
    filters.value = { ...filters.value, statuses: queryStatuses(status) }
  },
  { immediate: true },
)

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
      <section class="flex flex-col border-default border bg-elevated/20">
        <div
          class="flex flex-wrap items-center justify-between gap-3 border-b border-default bg-default/70 px-4 py-2.5"
        >
          <div>
            <div class="text-sm font-medium text-highlighted">Vehicle directory</div>
            <div class="text-xs text-muted">Current reports from the selected realtime feed</div>
          </div>
          <div class="flex flex-wrap items-center justify-end gap-2">
            <VehicleFilters v-model="filters" :feed-code="selectedFeedCode" />
            <span class="whitespace-nowrap text-xs text-dimmed"
              >{{ filtered.length.toLocaleString() }} vehicles</span
            >
          </div>
        </div>
        <div class="min-h-0">
          <div v-if="showSkeleton" class="flex flex-col gap-2 p-3">
            <USkeleton v-for="i in 6" :key="i" class="h-10 w-full" />
          </div>
          <VehicleList v-else :vehicles="filtered" @select="openVehicle" />
        </div>
      </section>
    </div>
  </AppPage>
</template>
