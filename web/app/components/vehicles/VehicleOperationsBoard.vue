<script setup lang="ts">
import { useVehicleDetailQuery } from '~/composables/useRestRealtime'
import { useAgenciesDetailQuery } from '~/composables/useRestGtfs'
import type { VehicleRow } from '~/types/vehicles'
import type { LngLatBoundsExtent } from '~/utils/joins/extent'
import VehicleFilters from '~/components/vehicles/VehicleFilters.vue'
import VehicleList from '~/components/vehicles/VehicleList.vue'
import VehicleMap, { type SelectedRouteOverlay } from '~/components/vehicles/VehicleMap.vue'
import { emptyFilters, filterVehicles } from '~/utils/vehicleFilters'
import { chosenAlgorithm, routeLine, stopFeatureCollection } from '~/utils/vehicleDetail'
import { hexColor } from '~/utils/gtfs'
import { usePollControl } from '~/composables/usePollControl'
import { useVehicleListPrefs } from '~/composables/useVehicleListPrefs'
import { useFeeds } from '~/composables/useFeeds'


const props = defineProps<{
  title?: string
  description?: string
  vehicles: VehicleRow[]
  loading: boolean
  error?: { message: string } | null
  feedCode: string | null
  extent?: LngLatBoundsExtent | null
  /** The feed's network geometry features, drawn under the fleet. */
  networkFeatures?: GeoJSON.Feature[]
}>()

const { locale } = useI18n()

const emit = defineEmits<{
  selectVehicle: [vehicleId: string]
  networkArmed: []
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
const { showTripDetails, boardListCollapsed } = useVehicleListPrefs()
const { feedPath, selectedFeed } = useFeeds()

const selectedPredictionAlgorithm = computed(() =>
  chosenAlgorithm(selectedFeed.value?.predictionAlgorithms ?? []),
)

const { result: selectedVehicleResult } = useVehicleDetailQuery(() => ({
  variables: {
    feedCode: props.feedCode ?? '', vehicleId: selectedVehicleId.value ?? '', algorithm: selectedPredictionAlgorithm.value,
  },
  enabled: !!props.feedCode && !!selectedVehicleId.value, pollInterval: intervalMs.value,
}))

const { result: agenciesResult } = useAgenciesDetailQuery(() => ({
  variables: { feedCode: props.feedCode ?? '' },
  enabled: !!props.feedCode,
}))
const agencyTimezone = computed(
  () => agenciesResult.value?.agencies.find((agency) => agency.agencyTimezone)?.agencyTimezone,
)

const selectedRoute = computed<SelectedRouteOverlay | null>(() => {
  const vehicle = selectedVehicleResult.value?.vehicle
  if (!vehicle) return null

  return {
    vehicleId: vehicle.vehicleId,
    line: routeLine(vehicle),
    stops: stopFeatureCollection(
      vehicle,
      selectedVehicleResult.value?.vehiclePredictions ?? [],
      selectedPredictionAlgorithm.value,
      agencyTimezone.value ?? undefined,
      locale.value,
    ),
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

function viewVehicleDetails(vehicleId: string) {
  navigateTo(feedPath(`/vehicles/${encodeURIComponent(vehicleId)}`))
}

function viewRouteDetails(routeId: string) {
  navigateTo(feedPath(`/explore/routes/${encodeURIComponent(routeId)}`))
}

// The vehicle list floats over the map's right edge and can be folded away to a button.
const panel = ref<HTMLElement | null>(null)
const { width: panelWidth } = useElementSize(panel)
/** Map space covered by the open panel (its width plus the right margin), so fits avoid it. */
const PANEL_MARGIN_PX = 12
const mapInsetRight = computed(() =>
  boardListCollapsed.value || !panelWidth.value ? 0 : panelWidth.value + PANEL_MARGIN_PX,
)
</script>

<template>
  <div
    id="operations-board"
    data-testid="vehicle-operations-board"
    class="relative h-full min-h-0 w-full overflow-hidden"
    :aria-label="$t('vehicles.board.ariaLabel')"
  >
    <VehicleMap
      :vehicles="filteredVehicles"
      :extent="extent"
      :feed-code="feedCode"
      :selected-vehicle-id="selectedVehicleId"
      :selected-route="selectedRoute"
      :inset-right="mapInsetRight"
      :network-features="networkFeatures"
      @select="selectVehicle"
      @view-trip="viewTripDetails"
      @view-block="viewBlockDetails"
      @view-route="viewRouteDetails"
      @view-vehicle="viewVehicleDetails"
      @network-armed="emit('networkArmed')"
    />

    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="translate-x-6 opacity-0"
      leave-active-class="transition duration-150 ease-in"
      leave-to-class="translate-x-6 opacity-0"
    >
    <section
      v-if="!boardListCollapsed"
      ref="panel"
      class="absolute inset-y-3 right-3 z-10 flex w-[min(26rem,calc(100%-1.5rem))] flex-col overflow-hidden rounded-lg border border-default bg-default/95 shadow-xl backdrop-blur"
      aria-labelledby="vehicle-directory-heading"
    >
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
          <div class="flex shrink-0 items-center gap-1">
            <slot name="actions" />
            <UTooltip :text="$t('vehicles.board.collapse')">
              <UButton
                icon="i-lucide-panel-right-close"
                color="neutral"
                variant="ghost"
                square
                :aria-label="$t('vehicles.board.collapse')"
                @click="boardListCollapsed = true"
              />
            </UTooltip>
          </div>
        </div>

        <div
          class="mt-3 flex flex-wrap items-center gap-1.5"
          :aria-label="$t('vehicles.board.fleetSummary')">
          <UBadge color="neutral" variant="subtle" size="sm"
            >{{
            $t('vehicles.board.reporting', { count: vehicles.length }, vehicles.length)
          }}</UBadge
          >
          <UBadge color="success" variant="subtle" size="sm">{{
            $t('vehicles.board.matched', { count: matchedVehicles }, matchedVehicles)
          }}</UBadge>
          <UBadge v-if="attentionVehicles" color="warning" variant="subtle" size="sm"
            >{{
            $t('vehicles.board.needAttention', { count: attentionVehicles }, attentionVehicles)
          }}</UBadge
          >

          <USwitch v-model="showTripDetails" class="ml-auto" size="xs" :label="$t('vehicles.tripAndBlock')" />
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
            :title="$t('vehicles.feedUnavailable')"
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
          <p class="text-sm font-medium text-highlighted">{{ $t('vehicles.noMatch') }}</p>
          <UButton
            color="neutral"
            variant="soft"
            :label="$t('vehicles.clearFilters')"
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
    </section>
    </Transition>

    <UButton
      v-if="boardListCollapsed"
      class="absolute right-3 top-3 z-10 shadow-lg"
      color="neutral"
      variant="outline"
      icon="i-lucide-panel-right-open"
      :aria-label="$t('vehicles.board.expand')"
      @click="boardListCollapsed = false"
    >
      {{ title ?? $t('vehicles.board.expand') }}
      <UBadge color="neutral" variant="subtle" size="sm">{{ vehicles.length }}</UBadge>
    </UButton>
  </div>
</template>
