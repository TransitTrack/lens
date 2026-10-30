<script setup lang="ts">
import type { SplitterItem } from '@nuxt/ui'
import type { VehicleRow } from '~/composables/useVehiclePolling'
import type { LngLatBoundsExtent } from '~/composables/useFeedExtent'
import VehicleFilters from '~/components/VehicleFilters.vue'
import VehicleList from '~/components/VehicleList.vue'
import VehicleMap, { type SelectedRouteOverlay } from '~/components/VehicleMap.vue'
import { emptyFilters, filterVehicles } from '~/utils/vehicleFilters'
import { routeLine, stopFeatureCollection } from '~/utils/vehicleDetail'
import { hexColor } from '~/utils/gtfs'
import { usePollControl } from '~/composables/usePollControl'
import { useVehicleListPrefs } from '~/composables/useVehicleListPrefs'
import { useFeeds } from '~/composables/useFeeds'
import { useVehicleDetailQuery } from '~~/generated/graphql'

const props = defineProps<{
  title?: string
  description?: string
  vehicles: VehicleRow[]
  loading: boolean
  error: Error | null
  feedCode: string | null
  extent?: LngLatBoundsExtent | null
  directoryDefaultSize?: number
}>()

const emit = defineEmits<{
  selectVehicle: [vehicleId: string]
}>()

const filters = ref(emptyFilters())
const selectedVehicleId = ref<string | null>(null)
const filteredVehicles = computed(() => filterVehicles(props.vehicles, filters.value))
const showSkeleton = computed(() => props.loading && props.vehicles.length === 0)
const matchedVehicles = computed(
  () => props.vehicles.filter((vehicle) => vehicle.matched && !vehicle.stale).length,
)
const attentionVehicles = computed(
  () => props.vehicles.filter((vehicle) => vehicle.stale || !vehicle.matched).length,
)

const { intervalMs } = usePollControl()
const { showTripDetails } = useVehicleListPrefs()
const { feedPath } = useFeeds()

const { result: selectedVehicleResult } = useVehicleDetailQuery(
  () => ({ feedCode: props.feedCode ?? '', vehicleId: selectedVehicleId.value ?? '' }),
  () => ({
    enabled: !!props.feedCode && !!selectedVehicleId.value,
    pollInterval: intervalMs.value,
  }),
)

const selectedRoute = computed<SelectedRouteOverlay | null>(() => {
  const vehicle = selectedVehicleResult.value?.vehicle
  if (!vehicle) return null

  return {
    vehicleId: vehicle.vehicleId,
    line: routeLine(vehicle),
    stops: stopFeatureCollection(vehicle),
    routeColor: hexColor(vehicle.trip?.route?.routeColor, '#2563eb'),
  }
})

function selectVehicle(vehicleId: string) {
  selectedVehicleId.value = vehicleId
}

function viewTripDetails(tripId: string) {
  navigateTo(feedPath(`/explore/trips/${encodeURIComponent(tripId)}`))
}

function viewBlockDetails(block: { blockId: string; serviceId: string }) {
  navigateTo({
    path: feedPath(`/explore/blocks/${encodeURIComponent(block.blockId)}`),
    query: { service: block.serviceId },
  })
}

function viewRouteDetails(routeId: string) {
  navigateTo(feedPath(`/explore/routes/${encodeURIComponent(routeId)}`))
}

function viewVehicleDetails(vehicleId: string) {
  navigateTo(feedPath(`/vehicles/${encodeURIComponent(vehicleId)}`))
}

// Side-by-side on desktop; stacked (directory above map) on narrow viewports.
const isDesktop = useMediaQuery('(min-width: 1024px)')
const splitterOrientation = computed(() => (isDesktop.value ? 'horizontal' : 'vertical'))
const desktopDirectorySize = computed(() =>
  Math.min(Math.max(props.directoryDefaultSize ?? 27, 20), 40),
)

const splitterItems = computed<SplitterItem[]>(() => [
  {
    id: 'directory',
    slot: 'directory',
    defaultSize: isDesktop.value ? desktopDirectorySize.value : 50,
    minSize: 20,
    maxSize: isDesktop.value ? 40 : 80,
    class: 'flex min-h-0 flex-col overflow-hidden',
  },
  {
    id: 'map',
    slot: 'map',
    defaultSize: isDesktop.value ? 100 - desktopDirectorySize.value : 50,
    class: 'flex min-h-0 flex-col overflow-hidden',
  },
])
</script>

<template>
  <USplitter
    id="operations-board"
    data-testid="vehicle-operations-board"
    :orientation="splitterOrientation"
    :items="splitterItems"
    class="h-full min-h-0 w-full"
    aria-label="Live vehicle operations"
  >
    <template #directory>
      <header class="shrink-0 border-b border-default bg-elevated/20 px-2 py-2 sm:px-3">
        <div v-if="title || description" class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <h1
              v-if="title"
              id="vehicle-directory-heading"
              class="truncate text-lg font-semibold text-highlighted tracking-tight"
            >
              {{ title }}
            </h1>
            <p v-if="description" class="truncate text-sm text-muted leading-snug">
              {{ description }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <slot name="actions" />
          </div>
        </div>

        <div class="mt-3 flex flex-wrap items-center gap-1.5" aria-label="Fleet summary">
          <UBadge color="neutral" variant="subtle" size="sm"
            >{{ vehicles.length }} reporting</UBadge
          >
          <UBadge color="success" variant="subtle" size="sm">{{ matchedVehicles }} matched</UBadge>
          <UBadge v-if="attentionVehicles" color="warning" variant="subtle" size="sm"
            >{{ attentionVehicles }} need attention</UBadge
          >

          <USwitch v-model="showTripDetails" class="ml-auto" size="xs" label="Trip & block" />
        </div>

        <div class="mt-2">
          <VehicleFilters v-model="filters" :vehicles="vehicles" />
        </div>
      </header>

      <div class="min-h-0 flex-1 overflow-auto">
        <div v-if="error" class="p-4">
          <UAlert
            color="error"
            variant="soft"
            icon="i-lucide-alert-triangle"
            title="Vehicle feed unavailable"
            :description="error.message"
          />
        </div>
        <div v-else-if="showSkeleton" class="flex flex-col gap-2 p-3">
          <USkeleton v-for="index in 7" :key="index" class="h-13 w-full" />
        </div>
        <div
          v-else-if="filteredVehicles.length === 0"
          class="flex h-full flex-col items-center justify-center gap-2 p-8 text-center"
        >
          <UIcon name="i-lucide-search-x" class="size-7 text-dimmed" />
          <p class="text-sm font-medium text-highlighted">No vehicles match these filters</p>
          <UButton
            color="neutral"
            variant="soft"
            label="Clear filters"
            @click="filters = emptyFilters()"
          />
        </div>
        <VehicleList
          v-else
          :vehicles="filteredVehicles"
          :selected-vehicle-id="selectedVehicleId"
          @select="selectVehicle"
          @open-details="emit('selectVehicle', $event)"
        />
      </div>
    </template>

    <template #map>
      <VehicleMap
        :vehicles="filteredVehicles"
        :extent="extent"
        :feed-code="feedCode"
        :selected-vehicle-id="selectedVehicleId"
        :selected-route="selectedRoute"
        selection-action-label="Highlight route"
        @select="selectVehicle"
        @view-trip="viewTripDetails"
        @view-block="viewBlockDetails"
        @view-route="viewRouteDetails"
        @view-vehicle="viewVehicleDetails"
      />
    </template>

    <template #resize-handle>
      <SplitterGrip :orientation="splitterOrientation" />
    </template>
  </USplitter>
</template>
