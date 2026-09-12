<script setup lang="ts">
import type {VehicleRow} from '../composables/useVehiclePolling'
import {colorForVehicle} from '../utils/vehicleMarker'
import {adherenceBadge} from '../utils/adherence'
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

// nuxt-maplibre auto-imports vue-maplibre-gl's `useMap` as `useMglMap`. It hands
// back the maplibre-gl Map registered under MAP_ID: `map.map` is the raw instance
// and `map.isLoaded` flips true once style + canvas are ready (fitBounds/flyTo
// before that silently no-op).
const map = useMglMap(MAP_ID)

// The route/stop network is a large, decorative underlay (a whole agency's
// shapes can be tens of thousands of coordinate points) behind the live
// fleet, which is what actually matters for first paint. Hold the query off
// until the map has loaded and the browser is idle, so fetching + building
// that GeoJSON doesn't compete with map/marker setup for CPU while the page
// is still loading.
const networkFeedCode = ref<string | null>(null)
let networkArmed = false
watch(
  () => map.isLoaded,
  (loaded) => {
    if (!loaded || networkArmed) return
    networkArmed = true
    const start = () => {
      networkFeedCode.value = props.feedCode ?? null
    }
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(start, {timeout: 2000})
    } else {
      setTimeout(start, 300)
    }
  },
  {immediate: true},
)
// Once armed, keep it synced to actual feed switches.
watch(
  () => props.feedCode,
  (fc) => {
    if (networkArmed) networkFeedCode.value = fc ?? null
  },
)

const {lines: networkLines, stops: networkStops} = useFeedGeometry(networkFeedCode)

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

// A fleet of ~150 `MglMarker`s is 150 real Vue components + DOM nodes, all
// re-rendered by Vue's reactivity on every poll tick — this was the source
// of the multi-second main-thread stall on refresh. A GeoJSON source backing
// a single symbol layer moves that work into maplibre's native `setData`,
// which diffs/repaints on the compositor instead of the Vue tree.
type VehicleState = 'stale' | 'matched' | 'unmatched'

function stateFor(v: VehicleRow): VehicleState {
  if (v.stale) return 'stale'
  if (v.matched) return 'matched'
  return 'unmatched'
}

const STATE_COLORS: Record<VehicleState, string> = {
  stale: colorForVehicle(false, true),
  matched: colorForVehicle(true, false),
  unmatched: colorForVehicle(false, false),
}

const vehiclesGeoJson = computed<GeoJSON.FeatureCollection>(() => ({
  type: 'FeatureCollection',
  features: props.vehicles
    .filter((v) => v.position)
    .map((v) => {
      const hasBearing = v.bearing != null && Number.isFinite(v.bearing)
      return {
        type: 'Feature',
        geometry: {type: 'Point', coordinates: [v.position.lon, v.position.lat]},
        properties: {
          vehicleId: v.vehicleId,
          state: stateFor(v),
          hasBearing,
          bearing: hasBearing ? v.bearing : 0,
        },
      } satisfies GeoJSON.Feature
    }),
}))

// Same visual language as VehicleHeadingMarker.vue (`.vhm--halo`, `.vhm__arrow`,
// `.vhm__dot`), pre-rendered to canvas: an arrow variant for vehicles with a
// bearing, a dot variant for those without, one per status color.
function drawVehicleIcon(color: string, arrow: boolean): ImageData {
  const size = 40
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!

  const cx = size / 2
  const cy = size / 2
  const haloR = size * 0.475

  // .vhm--halo
  ctx.beginPath()
  ctx.arc(cx, cy, haloR, 0, Math.PI * 2)
  ctx.fillStyle = '#ffffff'
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = 4
  ctx.shadowOffsetY = 1
  ctx.fill()
  ctx.shadowColor = 'transparent'

  if (arrow) {
    // .vhm__arrow: SVG path `M12 2 L20 21 L12 16 L4 21 Z`, viewBox 24, ~78%
    // of the halo diameter (matches the 18px arrow inside a 19px halo).
    const scale = ((haloR * 2) / 24) * 0.78
    ctx.save()
    ctx.translate(cx, cy)
    ctx.scale(scale, scale)
    ctx.translate(-12, -11.5)
    ctx.beginPath()
    ctx.moveTo(12, 2)
    ctx.lineTo(20, 21)
    ctx.lineTo(12, 16)
    ctx.lineTo(4, 21)
    ctx.closePath()
    ctx.fillStyle = color
    ctx.fill()
    ctx.lineWidth = 1.5 / scale
    ctx.strokeStyle = '#ffffff'
    ctx.stroke()
    ctx.restore()
  } else {
    // .vhm__dot: colored circle, 2px white border
    const dotR = haloR * 0.737
    ctx.beginPath()
    ctx.arc(cx, cy, dotR, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.lineWidth = 2
    ctx.strokeStyle = '#ffffff'
    ctx.stroke()
  }

  return ctx.getImageData(0, 0, size, size)
}

function iconId(state: VehicleState, arrow: boolean): string {
  return `vehicle-${arrow ? 'arrow' : 'dot'}-${state}`
}

const vehicleIcons: Record<string, ImageData> = {}
for (const [state, color] of Object.entries(STATE_COLORS) as [VehicleState, string][]) {
  vehicleIcons[iconId(state, true)] = drawVehicleIcon(color, true)
  vehicleIcons[iconId(state, false)] = drawVehicleIcon(color, false)
}

function registerVehicleIcons() {
  const gl = map.map
  if (!gl) return
  for (const [id, image] of Object.entries(vehicleIcons)) {
    if (!gl.hasImage(id)) gl.addImage(id, image, {pixelRatio: 2})
  }
}

watch(
  () => map.isLoaded,
  (loaded) => {
    if (!loaded) return
    registerVehicleIcons()
    // Re-add after a style swap (e.g. light/dark basemap change) drops the sprite.
    map.map?.on('styleimagemissing', registerVehicleIcons)
  },
  {immediate: true},
)

function onVehicleClick(e: { features?: { properties?: Record<string, unknown> }[] }) {
  const id = e.features?.[0]?.properties?.vehicleId
  if (typeof id === 'string') activeVehicleId.value = id
}
</script>

<template>
  <!-- MglMap renders a fragment root, so it can't take a `class`; size it here. -->
  <div class="relative h-full w-full">
    <MglMap
      :map-key="MAP_ID"
      :map-style="mapStyle"
      :bounds="initialBounds"
      :fit-bounds-options="{ padding: 40, animate: true }"
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
            <UBadge :color="badge(activeVehicle).color" variant="subtle">
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

      <!-- `before` pins it below the vehicle icon layer explicitly — it's
           mounted lazily (see `networkFeedCode` above), well after the
           vehicle layer, so relying on maplibre's default "later-added
           layers paint on top" insertion order would put it above instead. -->
      <FeedNetworkLayer
        v-if="networkFeedCode"
        :lines="networkLines"
        :stops="networkStops"
        before="vehicles-icons"
        dim
      />

      <MglGeoJsonSource source-id="vehicles" :data="vehiclesGeoJson">
        <MglSymbolLayer
          layer-id="vehicles-icons"
          :layout="{
            'icon-image': [
              'match',
              ['get', 'state'],
              'stale',
              ['case', ['get', 'hasBearing'], iconId('stale', true), iconId('stale', false)],
              'matched',
              ['case', ['get', 'hasBearing'], iconId('matched', true), iconId('matched', false)],
              ['case', ['get', 'hasBearing'], iconId('unmatched', true), iconId('unmatched', false)],
            ],
            'icon-rotate': ['get', 'bearing'],
            'icon-rotation-alignment': 'map',
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
          }"
          @click="onVehicleClick"
        />
      </MglGeoJsonSource>
    </MglMap>

    <VehicleMapLegend class="absolute bottom-2 left-2"/>

    <UButton
      class="absolute right-14 top-3 shadow"
      color="neutral"
      variant="solid"
      icon="i-lucide-scan"
      label="Fit fleet"
      @click="fit"
    />
  </div>
</template>
