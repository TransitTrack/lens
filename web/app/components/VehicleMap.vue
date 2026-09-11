<script setup lang="ts">
import 'maplibre-gl/dist/maplibre-gl.css'
import type {VehicleRow} from '../composables/useVehiclePolling'
import {colorForVehicle} from '../utils/vehicleMarker'
import {adherenceBadge} from '../utils/adherence'
import VehicleHeadingMarker from './VehicleHeadingMarker.vue'
import VehicleMapLegend from './VehicleMapLegend.vue'
import FeedNetworkLayer from './FeedNetworkLayer.vue'
import {useMapStyle} from '../composables/useMapStyle'
import {useFeedGeometry} from '../composables/useFeedGeometry'
import {extentToBounds} from '../utils/mapBounds'
import type {LngLatBoundsExtent} from '../composables/useFeedExtent'

const props = defineProps<{
  vehicles: VehicleRow[]
  extent?: LngLatBoundsExtent | null
  /** GTFS feed code — draws the route + stop network as a base layer */
  feedCode?: string | null
}>()
const emit = defineEmits<{ (e: 'select', vehicleId: string): void }>()

const MAP_ID = 'vehicle-map'
const mapStyle = useMapStyle()

const {lines: networkLines, stops: networkStops} = useFeedGeometry(
  toRef(props, 'feedCode') as Ref<string | null>,
)

// nuxt-maplibre auto-imports vue-maplibre-gl's `useMap` as `useMglMap`. It hands
// back the maplibre-gl Map registered under MAP_ID: `map.map` is the raw instance
// and `map.isLoaded` flips true once style + canvas are ready (fitBounds/flyTo
// before that silently no-op).
const map = useMglMap(MAP_ID)

// Capture the agency extent once — `useFeedExtent` re-emits a fresh object on
// every Apollo tick and binding it live would snap the view back on each poll.
const initialBounds = shallowRef(extentToBounds(props.extent))
watch(
  () => props.extent,
  (e) => {
    if (!initialBounds.value) initialBounds.value = extentToBounds(e)
  },
)

const activeVehicleId = ref<string | null>(null)
const activeVehicle = computed(
  () => props.vehicles.find((v) => v.vehicleId === activeVehicleId.value) ?? null,
)

function clipped(values: number[], lo = 0.02, hi = 0.98): [number, number] {
  const sorted = [...values].sort((a, b) => a - b)
  const at = (q: number) =>
    sorted[Math.min(sorted.length - 1, Math.max(0, Math.floor(q * (sorted.length - 1))))]!
  return [at(lo), at(hi)]
}

function boundsForFit(): [[number, number], [number, number]] | null {
  // Prefer where the vehicles actually are — the agency's GTFS extent can be far
  // wider than the live fleet. Clip to the 2nd–98th percentile so one bad GPS
  // fix doesn't blow out the view.
  const pts = props.vehicles.filter((v) => v.position)
  if (pts.length >= 3) {
    const [minLon, maxLon] = clipped(pts.map((v) => v.position.lon))
    const [minLat, maxLat] = clipped(pts.map((v) => v.position.lat))
    return [
      [minLon, minLat],
      [maxLon, maxLat],
    ]
  }
  const e = props.extent
  if (e) {
    return [
      [e.minLon, e.minLat],
      [e.maxLon, e.maxLat],
    ]
  }
  if (pts.length > 0) {
    return [
      [Math.min(...pts.map((v) => v.position.lon)), Math.min(...pts.map((v) => v.position.lat))],
      [Math.max(...pts.map((v) => v.position.lon)), Math.max(...pts.map((v) => v.position.lat))],
    ]
  }
  return null
}

// First fit is instant — the map opened on the agency extent via `:bounds`.
let firstFit = true

function fit() {
  if (!map.map || !map.isLoaded) return
  const bounds = boundsForFit()
  if (!bounds) return
  // The panel layout can still be settling when the style first loads, leaving
  // the canvas at a stale size; resize before fitting so the zoom is right.
  map.map.resize()
  map.map.fitBounds(bounds, {padding: 40, maxZoom: 15, duration: firstFit ? 0 : 800})
  firstFit = false
}

// Re-fit only when the *fleet* changes (vehicles added/removed) or the map
// finishes loading — NOT on every poll, which just nudges positions and would
// otherwise ease the view a little each refresh. Manual re-frame: "Fit fleet".
const fleetKey = computed(() =>
  props.vehicles
    .map((v) => v.vehicleId)
    .sort()
    .join(','),
)
watch(
  [fleetKey, () => map.isLoaded],
  () => {
    fit()
    requestAnimationFrame(fit)
  },
  {immediate: true},
)

function badge(v: VehicleRow) {
  return adherenceBadge(v.scheduleAdherenceSec)
}
</script>

<template>
  <!-- MglMap renders a fragment root, so it can't take a `class`; size it here. -->
  <div class="relative h-full w-full">
    <MglMap
      :map-key="MAP_ID"
      :map-style="mapStyle"
      :bounds="initialBounds"
      :fit-bounds-options="{ padding: 40, animate: false }"
    >
      <MglNavigationControl/>

      <MglPopup
        v-if="activeVehicle"
        :key="activeVehicle.vehicleId"
        :coordinates="[activeVehicle.position.lon, activeVehicle.position.lat]"
        :close-on-click="false"
        :offset="14"
        @close="activeVehicleId = null"
      >
        <div class="flex min-w-48 flex-col gap-2 p-1">
          <div class="flex items-center justify-between gap-2">
            <span class="font-semibold text-highlighted">
              {{ activeVehicle.label ?? activeVehicle.vehicleId }}
            </span>
            <UBadge
              v-if="activeVehicle.trip?.route?.routeShortName"
              size="sm"
              :style="{
                backgroundColor: activeVehicle.trip.route.routeColor
                  ? `#${activeVehicle.trip.route.routeColor.replace('#', '')}`
                  : undefined,
              }"
            >
              {{ activeVehicle.trip.route.routeShortName }}
            </UBadge>
          </div>
          <div v-if="activeVehicle.trip?.tripHeadsign" class="text-xs text-muted">
            {{ activeVehicle.trip.tripHeadsign }}
          </div>
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
            <UBadge :color="badge(activeVehicle).color" variant="subtle" size="sm">
              {{ badge(activeVehicle).label }}
            </UBadge>
            <span class="text-muted">
              {{ activeVehicle.speedMps != null ? Math.round(activeVehicle.speedMps * 3.6) : '—' }}
              km/h
            </span>
            <span v-if="activeVehicle.stale" class="text-warning">stale</span>
          </div>
          <UButton
            size="xs"
            color="neutral"
            variant="soft"
            trailing-icon="i-lucide-arrow-right"
            block
            label="Open details"
            @click="emit('select', activeVehicle.vehicleId)"
          />
        </div>
      </MglPopup>

      <MglMarker
        v-for="v in props.vehicles"
        :key="v.vehicleId"
        :coordinates="[v.position.lon, v.position.lat]"
      >
        <template #marker>
          <VehicleHeadingMarker
            :color="colorForVehicle(v.matched, v.stale)"
            :bearing="v.bearing"
            :size="18"
            halo
            @click="activeVehicleId = v.vehicleId"
          />
        </template>
      </MglMarker>

      <FeedNetworkLayer
        v-if="props.feedCode"
        :lines="networkLines"
        :stops="networkStops"
        dim
      />
    </MglMap>

    <VehicleMapLegend class="absolute bottom-2 left-2"/>

    <UButton
      class="absolute left-2 top-2 shadow"
      color="neutral"
      variant="solid"
      size="sm"
      icon="i-lucide-scan"
      label="Fit fleet"
      @click="fit"
    />
  </div>
</template>
