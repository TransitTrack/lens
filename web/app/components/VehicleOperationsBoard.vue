<script setup lang="ts">
import type { VehicleRow } from '~/composables/useVehiclePolling'
import type { LngLatBoundsExtent } from '~/composables/useFeedExtent'
import VehicleFilters from '~/components/VehicleFilters.vue'
import VehicleList from '~/components/VehicleList.vue'
import VehicleMap, { type SelectedRouteOverlay } from '~/components/VehicleMap.vue'
import { emptyFilters, filterVehicles } from '~/utils/vehicleFilters'
import { routeLine, stopFeatureCollection } from '~/utils/vehicleDetail'
import { hexColor } from '~/utils/gtfs'
import { useVehicleDetailQuery } from '~~/generated/graphql'

const props = defineProps<{
  vehicles: VehicleRow[]
  loading: boolean
  error: Error | null
  feedCode: string | null
  extent?: LngLatBoundsExtent | null
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

const { result: selectedVehicleResult, loading: selectedVehicleLoading } = useVehicleDetailQuery(
  () => ({ feedCode: props.feedCode ?? '', vehicleId: selectedVehicleId.value ?? '' }),
  () => ({ enabled: !!props.feedCode && !!selectedVehicleId.value }),
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

const selectedVehicle = computed(() =>
  props.vehicles.find((vehicle) => vehicle.vehicleId === selectedVehicleId.value) ?? null,
)

function selectVehicle(vehicleId: string) {
  selectedVehicleId.value = vehicleId
}

</script>

<template>
  <section
    data-testid="vehicle-operations-board"
    class="operations-board grid min-h-0 flex-1 overflow-hidden bg-default lg:grid-cols-[minmax(23rem,0.78fr)_minmax(0,1.65fr)]"
    aria-label="Live vehicle operations"
  >
    <aside
      data-testid="operations-vehicle-directory"
      class="operations-board__directory flex min-h-0 flex-col border-b border-default lg:border-r lg:border-b-0"
      aria-labelledby="vehicle-directory-heading"
    >
      <header class="shrink-0 border-b border-default bg-elevated/20 px-2 py-2 sm:px-5">
        <div class="mt-3 flex flex-wrap justify-between gap-x-4 gap-y-1.5 text-xs text-muted" aria-label="Fleet summary">
          <span><strong class="font-semibold text-highlighted">{{ vehicles.length }}</strong> reporting</span>
          <span><strong class="font-semibold text-success">{{ matchedVehicles }}</strong> matched</span>
          <span v-if="attentionVehicles"><strong class="font-semibold text-warning">{{ attentionVehicles }}</strong> need attention</span>

          <div class="flex-1 justify-end">
            <span class="rounded-md bg-primary/10 px-2 py-1 text-xs font-semibold text-primary">
              {{ filteredVehicles.length.toLocaleString() }} shown
            </span>
          </div>
        </div>

        <div class="mt-3">
          <VehicleFilters v-model="filters" :feed-code="feedCode" />
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
        <div v-else-if="filteredVehicles.length === 0" class="flex h-full flex-col items-center justify-center gap-2 p-8 text-center">
          <UIcon name="i-lucide-search-x" class="size-7 text-dimmed" />
          <p class="text-sm font-medium text-highlighted">No vehicles match these filters</p>
          <UButton color="neutral" variant="soft" label="Clear filters" @click="filters = emptyFilters()" />
        </div>
        <VehicleList
          v-else
          :vehicles="filteredVehicles"
          :selected-vehicle-id="selectedVehicleId"
          @select="selectVehicle"
          @open-details="emit('selectVehicle', $event)"
        />
      </div>
    </aside>

    <div class="operations-board__map relative min-h-96 overflow-hidden lg:min-h-0">
      <VehicleMap
        :vehicles="filteredVehicles"
        :extent="extent"
        :feed-code="feedCode"
        :selected-vehicle-id="selectedVehicleId"
        :selected-route="selectedRoute"
        selection-action-label="Highlight route"
        @select="selectVehicle"
      />
      <div class="pointer-events-none absolute left-4 top-4 rounded-lg border border-default bg-default/90 px-3 py-2 shadow-sm backdrop-blur-sm">
        <div class="flex items-center gap-2 text-sm font-semibold text-highlighted">
          <span class="size-2 rounded-full bg-success" aria-hidden="true" />
          {{ selectedVehicle ? selectedVehicle.label ?? selectedVehicle.vehicleId : 'Fleet map' }}
        </div>
        <p class="mt-0.5 text-xs text-muted">
          <template v-if="selectedVehicleLoading">Loading trip shape and stops…</template>
          <template v-else-if="selectedRoute?.line.length">Selected trip shape and stops</template>
          <template v-else-if="selectedVehicle">No scheduled shape is available for this vehicle.</template>
          <template v-else>{{ filteredVehicles.length }} live position{{ filteredVehicles.length === 1 ? '' : 's' }}</template>
        </p>
      </div>
    </div>
  </section>
</template>
