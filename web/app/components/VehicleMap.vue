<script setup lang="ts">
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge } from '../utils/adherence'
import VehicleMapLegend from './VehicleMapLegend.vue'
import FeedNetworkLayer from './FeedNetworkLayer.vue'
import { useMapStyle } from '../composables/useMapStyle'
import { useFeedGeometry } from '../composables/useFeedGeometry'
import { extentToBounds } from '../utils/mapBounds'
import type { LngLatBoundsExtent } from '../composables/useFeedExtent'
import { haversineM } from '../utils/vehicleTelemetry'
import { usePollControl } from '../composables/usePollControl'
import { useAvlTrailQuery, useVehicleDetailQuery } from '~~/generated/graphql'

export interface SelectedRouteOverlay {
  vehicleId: string
  line: LngLat[]
  stops: GeoJSON.FeatureCollection
  routeColor: string
}

const props = withDefaults(
  defineProps<{
    vehicles: VehicleRow[]
    extent?: LngLatBoundsExtent | null
    /** GTFS feed code — draws the route + stop network as a base layer */
    feedCode?: string | null
    /** The selected vehicle's scheduled route and stop sequence. */
    selectedRoute?: SelectedRouteOverlay | null
    /** Vehicle currently being inspected; the map enters focused mode when set. */
    selectedVehicleId?: string | null
    /** Label for the marker-popup action. */
    selectionActionLabel?: string
  }>(),
  {
    extent: null,
    feedCode: null,
    selectedRoute: null,
    selectedVehicleId: null,
    selectionActionLabel: 'Open details',
  },
)
const emit = defineEmits<{ (e: 'select', vehicleId: string): void }>()

const MAP_ID = 'vehicle-map'
const mapStyle = useMapStyle()
const colorMode = useColorMode()
// Halo/outline behind a marker's colored glyph, for contrast against the
// basemap tiles (which already swap to a dark style with the app's color
// mode). The dark value is a lighter neutral than `--map-popup-bg` (matches
// `--map-popup-border` in main.css) so it still reads against dark basemap
// tiles instead of nearly disappearing into them.
const markerContrastColor = computed(() => (colorMode.value === 'dark' ? '#000000' : '#ffffff'))

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
const visibleLayers = reactive({
  networkRoutes: true,
  networkStops: true,
  selectedTripLine: true,
  selectedTripStops: true,
  vehicle: true,
})
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
      requestIdleCallback(start, { timeout: 2000 })
    } else {
      setTimeout(start, 300)
    }
  },
  { immediate: true },
)
// Once armed, keep it synced to actual feed switches.
watch(
  () => props.feedCode,
  (fc) => {
    if (networkArmed) networkFeedCode.value = fc ?? null
  },
)

const { lines: networkLines, stops: networkStops } = useFeedGeometry(networkFeedCode)

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

const { intervalMs } = usePollControl()

const { result: activeVehicleDetailResult, loading: activeVehicleDetailLoading } =
  useVehicleDetailQuery(
    () => ({ feedCode: props.feedCode ?? '', vehicleId: activeVehicleId.value ?? '' }),
    () => ({
      enabled: !!props.feedCode && !!activeVehicleId.value,
      pollInterval: intervalMs.value,
    }),
  )

const activeVehicleDetail = computed(() => {
  const vehicle = activeVehicleDetailResult.value?.vehicle
  return vehicle?.vehicleId === activeVehicleId.value ? vehicle : null
})

const { result: activeVehicleTrailResult } = useAvlTrailQuery(
  () => ({ feedCode: props.feedCode ?? '', vehicleId: activeVehicleId.value ?? '', limit: 2 }),
  () => ({ enabled: !!props.feedCode && !!activeVehicleId.value, pollInterval: intervalMs.value }),
)

const estimatedSpeedMps = computed(() => {
  if (activeVehicleDetail.value?.speedMps != null || activeVehicle.value?.speedMps != null)
    return null
  // avlReports are newest-first. A position-based estimate is only useful
  // over a short, valid interval; otherwise GPS drift can look like movement.
  const [latest, previous] = activeVehicleTrailResult.value?.avlReports ?? []
  if (
    !latest ||
    !previous ||
    latest.vehicleId !== activeVehicleId.value ||
    previous.vehicleId !== activeVehicleId.value
  )
    return null
  const elapsedSec = (Date.parse(latest.ts) - Date.parse(previous.ts)) / 1000
  const distanceM = haversineM(latest.position, previous.position)
  if (!Number.isFinite(elapsedSec) || elapsedSec < 5 || elapsedSec > 300 || distanceM == null)
    return null
  const speedMps = distanceM / elapsedSec
  return speedMps <= 45 ? speedMps : null
})

const activeSpeed = computed(() => {
  const reportedMps = activeVehicleDetail.value?.speedMps ?? activeVehicle.value?.speedMps
  if (reportedMps != null) return { mps: reportedMps, estimated: false }
  return estimatedSpeedMps.value == null ? null : { mps: estimatedSpeedMps.value, estimated: true }
})

const activeTripProgress = computed(() => {
  const vehicle = activeVehicleDetail.value
  const totalM = vehicle?.pattern?.lengthM
  const distanceAlongM = vehicle?.distanceAlongTripM
  if (totalM == null || totalM <= 0 || distanceAlongM == null) return null

  const travelledM = Math.min(totalM, Math.max(0, distanceAlongM))
  return {
    percentage: Math.round((travelledM / totalM) * 100),
    travelledKm: travelledM / 1000,
    totalKm: totalM / 1000,
    remainingKm: Math.max(0, totalM - travelledM) / 1000,
  }
})

const activeNextStop = computed(() => {
  const vehicle = activeVehicleDetail.value
  const stops = vehicle?.pattern?.stopPaths ?? []
  if (stops.length === 0) return null
  const index = vehicle?.stopPathIndex ?? -1
  return (
    [...stops]
      .sort((a, b) => a.stopPathIndex - b.stopPathIndex)
      .find((stop) => stop.stopPathIndex >= index)?.stop ?? null
  )
})

function clockTime(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  return [hours, minutes, secs].map((value) => String(value).padStart(2, '0')).join(':')
}

function updateAge(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000))
  if (seconds < 45) return 'a few seconds ago'
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`
  return `${Math.round(seconds / 3600)}h ago`
}

function updateTimestamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  const parts = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('day')}.${value('month')}.${value('year')}, ${value('hour')}:${value('minute')}:${value('second')}`
}

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
  map.map.fitBounds(bounds, { padding: 40, maxZoom: 15, duration: firstFit ? 0 : 800 })
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
  { immediate: true },
)

function badge(v: VehicleRow) {
  return adherenceBadge(v.scheduleAdherenceSec)
}

// A fleet of ~150 `MglMarker`s is 150 real Vue components + DOM nodes, all
// re-rendered by Vue's reactivity on every poll tick — this was the source
// of the multi-second main-thread stall on refresh. A GeoJSON source backing
// a single symbol layer moves that work into maplibre's native `setData`,
// which diffs/repaints on the compositor instead of the Vue tree.
type VehicleState = 'stale' | 'stopped' | 'matched' | 'unmatched'

const STOPPED_SPEED_MPS = 0.5
const STOPPED_DISTANCE_M = 25

function stateFor(v: VehicleRow): VehicleState {
  if (v.stale) return 'stale'
  if (
    v.speedMps != null &&
    v.speedMps <= STOPPED_SPEED_MPS &&
    v.distanceAlongTripM != null &&
    v.distanceAlongTripM < STOPPED_DISTANCE_M
  )
    return 'stopped'
  if (v.matched) return 'matched'
  return 'unmatched'
}

const STATE_COLORS: Record<VehicleState, string> = {
  stale: '#f59e0b',
  stopped: '#f50b0b',
  matched: '#22c55e',
  unmatched: '#9ca3af',
}

const vehiclesGeoJson = computed<GeoJSON.FeatureCollection>(() => ({
  type: 'FeatureCollection',
  features: props.vehicles
    .filter(
      (v) => v.position && (!props.selectedVehicleId || v.vehicleId === props.selectedVehicleId),
    )
    .map((v) => {
      const hasBearing = v.bearing != null && Number.isFinite(v.bearing)
      return {
        type: 'Feature',
        geometry: { type: 'Point', coordinates: [v.position.lon, v.position.lat] },
        properties: {
          vehicleId: v.vehicleId,
          state: stateFor(v),
          selected: v.vehicleId === props.selectedVehicleId,
          hasBearing,
          bearing: hasBearing ? v.bearing : 0,
        },
      } satisfies GeoJSON.Feature
    }),
}))

const selectedRouteLine = computed<GeoJSON.Feature>(() => ({
  type: 'Feature',
  properties: {},
  geometry: { type: 'LineString', coordinates: props.selectedRoute?.line ?? [] },
}))

const selectedRouteKey = computed(() => {
  const route = props.selectedRoute
  return route
    ? `${route.vehicleId}:${route.line.length}:${route.line[0]}:${route.line.at(-1)}`
    : ''
})

function fitSelectedRoute() {
  const line = props.selectedRoute?.line ?? []
  if (line.length < 2 || !map.map || !map.isLoaded) return
  const lngs = line.map(([lng]) => lng)
  const lats = line.map(([, lat]) => lat)
  map.map.fitBounds(
    [
      [Math.min(...lngs), Math.min(...lats)],
      [Math.max(...lngs), Math.max(...lats)],
    ],
    { padding: 56, maxZoom: 16, duration: 700 },
  )
}

watch([selectedRouteKey, () => map.isLoaded], fitSelectedRoute, { immediate: true })

function placeStopsAboveSelectedRoute() {
  const instance = map.map
  if (!instance || !map.isLoaded) return
  if (!instance.getLayer('selected-vehicle-stops-circles') || !instance.getLayer('vehicles-icons'))
    return
  // Placing stops immediately below the vehicle icons keeps them above both
  // selected-route layers without relying on a not-yet-registered `before` target.
  instance.moveLayer('selected-vehicle-stops-circles', 'vehicles-icons')
}

function placeFeedNetworkBelowSelectedRoute() {
  const instance = map.map
  if (!instance || !map.isLoaded) return

  const target = instance.getLayer('selected-vehicle-route-casing')
    ? 'selected-vehicle-route-casing'
    : 'vehicles-icons'

  for (const layerId of [
    'feed-network-routes-casing',
    'feed-network-routes-line',
    'feed-network-routes-hit',
    'feed-network-stops-circles',
  ]) {
    if (instance.getLayer(layerId)) instance.moveLayer(layerId, target)
  }
}

watch(
  [selectedRouteKey, () => map.isLoaded, networkFeedCode],
  () => {
    nextTick(() =>
      requestAnimationFrame(() => {
        placeFeedNetworkBelowSelectedRoute()
        placeStopsAboveSelectedRoute()
      }),
    )
  },
  { flush: 'post' },
)

// Same visual language as VehicleHeadingMarker.vue (`.vhm--halo`, `.vhm__arrow`,
// `.vhm__dot`), pre-rendered to canvas: an arrow variant for moving vehicles,
// a dot variant for those without a bearing, and an amber pause variant.
function drawVehicleIcon(
  color: string,
  arrow: boolean,
  paused: boolean,
  contrastColor: string,
): ImageData {
  const size = 42
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
  ctx.fillStyle = contrastColor
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = 4
  ctx.shadowOffsetY = 1
  ctx.fill()
  ctx.shadowColor = 'transparent'

  if (paused) {
    const dotR = haloR * 0.737
    ctx.beginPath()
    ctx.arc(cx, cy, dotR, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.lineWidth = 2
    ctx.strokeStyle = contrastColor
    ctx.stroke()

    ctx.fillStyle = contrastColor
    ctx.fillRect(cx - 5, cy - 7, 3.5, 14)
    ctx.fillRect(cx + 1.5, cy - 7, 3.5, 14)
  } else if (arrow) {
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
    ctx.strokeStyle = contrastColor
    ctx.stroke()
    ctx.restore()
  } else {
    // .vhm__dot: colored circle, 2px contrast border
    const dotR = haloR * 0.737
    const scale = ((haloR * 2) / 24) * 0.78
    ctx.beginPath()
    ctx.arc(cx, cy, dotR, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.lineWidth = 1.5 / scale
    ctx.strokeStyle = contrastColor
    ctx.stroke()
  }

  return ctx.getImageData(0, 0, size, size)
}

function iconId(state: VehicleState, arrow: boolean): string {
  return `vehicle-${arrow ? 'arrow' : 'dot'}-${state}`
}

function buildVehicleIcons(contrastColor: string): Record<string, ImageData> {
  const icons: Record<string, ImageData> = {}
  for (const [state, color] of Object.entries(STATE_COLORS) as [VehicleState, string][]) {
    const paused = state === 'stopped'
    icons[iconId(state, true)] = drawVehicleIcon(color, true, paused, contrastColor)
    icons[iconId(state, false)] = drawVehicleIcon(color, false, paused, contrastColor)
  }
  return icons
}

let vehicleIcons = buildVehicleIcons(markerContrastColor.value)

function registerVehicleIcons() {
  const gl = map.map
  if (!gl) return
  for (const [id, image] of Object.entries(vehicleIcons)) {
    if (!gl.hasImage(id)) gl.addImage(id, image, { pixelRatio: 2 })
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
  { immediate: true },
)

// The basemap style also swaps on color-mode change and usually drops the
// sprite itself (caught by `styleimagemissing` above), but force-update any
// icons still registered under the old colors too, in case the style swap
// hasn't completed yet or doesn't end up dropping them.
watch(markerContrastColor, (contrastColor) => {
  vehicleIcons = buildVehicleIcons(contrastColor)
  const gl = map.map
  if (!gl) return
  for (const [id, image] of Object.entries(vehicleIcons)) {
    if (gl.hasImage(id)) gl.updateImage(id, image)
    else gl.addImage(id, image, { pixelRatio: 2 })
  }
})

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
      :attribution-control="false"
      :fit-bounds-options="{ padding: 40, animate: true }"
    >
      <MglNavigationControl />

      <MglPopup
        v-if="activeVehicle"
        :key="activeVehicle.vehicleId"
        :coordinates="[activeVehicle.position.lon, activeVehicle.position.lat]"
        :close-on-click="true"
        :close-button="false"
        :close-on-move="true"
        :offset="14"
        max-width="22rem"
        class-name="vehicle-operation-popup"
        @close="activeVehicleId = null"
      >
        <div
          class="flex max-h-[min(40rem,calc(100vh-6rem))] w-[20rem] max-w-[calc(100vw-3rem)] flex-col gap-3 overflow-y-auto p-1 text-sm"
        >
          <header class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-lg font-bold text-highlighted">#{{ activeVehicle.vehicleId }}</p>
              <p
                v-if="activeVehicleDetail?.trip?.tripHeadsign ?? activeVehicle.trip?.tripHeadsign"
                class="truncate text-xs text-muted"
              >
                {{ activeVehicleDetail?.trip?.tripHeadsign ?? activeVehicle.trip?.tripHeadsign }}
              </p>
            </div>
            <UBadge
              v-if="
                activeVehicleDetail?.trip?.route?.routeShortName ??
                activeVehicle.trip?.route?.routeShortName
              "
              size="sm"
              :style="{
                backgroundColor:
                  (activeVehicleDetail?.trip?.route?.routeColor ??
                  activeVehicle.trip?.route?.routeColor)
                    ? `#${(activeVehicleDetail?.trip?.route?.routeColor ?? activeVehicle.trip?.route?.routeColor)?.replace('#', '')}`
                    : undefined,
              }"
            >
              {{
                activeVehicleDetail?.trip?.route?.routeShortName ??
                activeVehicle.trip?.route?.routeShortName
              }}
            </UBadge>
          </header>

          <div
            v-if="activeVehicleDetailLoading && !activeVehicleDetail"
            class="flex items-center gap-2 py-4 text-sm text-muted"
          >
            <UIcon name="i-lucide-loader-circle" class="size-4 animate-spin" />
            Loading operational details…
          </div>

          <template v-else-if="activeVehicleDetail">
            <section class="border-y border-dashed border-default py-2.5">
              <dl class="space-y-1.5">
                <div class="flex justify-between gap-3">
                  <dt class="text-muted">Route</dt>
                  <dd class="text-right font-semibold text-highlighted">
                    {{
                      activeVehicleDetail.trip?.route?.routeShortName ??
                      activeVehicleDetail.trip?.routeId ??
                      'Unassigned'
                    }}
                  </dd>
                </div>
                <div v-if="activeVehicleDetail.block" class="flex justify-between gap-3">
                  <dt class="text-muted">Block</dt>
                  <dd class="text-right font-semibold text-highlighted">
                    {{ activeVehicleDetail.block.blockId }}
                  </dd>
                </div>
                <div
                  v-if="
                    activeVehicleDetail.block?.startTimeSec != null ||
                    activeVehicleDetail.block?.endTimeSec != null
                  "
                  class="flex justify-between gap-3"
                >
                  <dt class="text-muted">Block window</dt>
                  <dd class="text-right font-semibold tabular-nums text-highlighted">
                    {{ clockTime(activeVehicleDetail.block?.startTimeSec) }}–{{
                      clockTime(activeVehicleDetail.block?.endTimeSec)
                    }}
                  </dd>
                </div>
              </dl>
            </section>

            <section v-if="activeVehicleDetail.trip" class="space-y-1.5">
              <p class="font-semibold text-highlighted">
                Trip {{ activeVehicleDetail.trip.tripId }}
              </p>
              <div class="flex justify-between gap-3 text-muted">
                <span>Scheduled</span>
                <span class="font-semibold tabular-nums text-highlighted">
                  {{ clockTime(activeVehicleDetail.trip.startTimeSec) }}–{{
                    clockTime(activeVehicleDetail.trip.endTimeSec)
                  }}
                </span>
              </div>
              <div v-if="activeTripProgress" class="pt-1">
                <div class="flex justify-between gap-3 text-xs">
                  <span class="font-semibold text-highlighted"
                    >Progress {{ activeTripProgress.percentage }}%</span
                  >
                  <span class="tabular-nums text-muted"
                    >{{ activeTripProgress.travelledKm.toFixed(1) }} /
                    {{ activeTripProgress.totalKm.toFixed(1) }} km</span
                  >
                </div>
                <div
                  class="mt-1.5 h-2 overflow-hidden rounded-full bg-elevated ring-1 ring-inset ring-default"
                >
                  <div
                    class="h-full bg-primary"
                    :style="{ width: `${activeTripProgress.percentage}%` }"
                  />
                </div>
                <p class="mt-1 text-right text-xs tabular-nums text-muted">
                  {{ activeTripProgress.remainingKm.toFixed(1) }} km remaining
                </p>
              </div>
            </section>

            <section class="border-y border-dashed border-default py-2.5">
              <dl class="space-y-1.5">
                <div class="flex justify-between gap-3">
                  <dt class="text-muted">At</dt>
                  <dd class="max-w-52 text-right font-semibold text-highlighted">
                    {{ activeVehicleDetail.currentStop?.stopName ?? 'Between stops' }}
                  </dd>
                </div>
                <div v-if="activeNextStop" class="flex justify-between gap-3">
                  <dt class="text-muted">Next stop</dt>
                  <dd class="max-w-52 text-right font-semibold text-highlighted">
                    {{ activeNextStop.stopName ?? activeNextStop.stopId }}
                  </dd>
                </div>
                <div class="flex justify-between gap-3">
                  <dt class="text-muted">Speed</dt>
                  <dd class="text-right font-semibold tabular-nums text-highlighted">
                    {{ activeSpeed ? `${Math.round(activeSpeed.mps * 3.6)} km/h` : 'Unavailable' }}
                    <span v-if="activeSpeed?.estimated" class="ml-1 text-xs font-medium text-muted"
                      >estimated</span
                    >
                  </dd>
                </div>
              </dl>
            </section>

            <section class="space-y-1.5">
              <div class="flex justify-between gap-3">
                <span class="text-muted">Last update</span>
                <span class="text-right font-semibold tabular-nums text-highlighted">{{
                  updateTimestamp(activeVehicleDetail.reportTs)
                }}</span>
              </div>
              <div class="flex justify-between gap-3">
                <span class="text-muted">Updated</span>
                <span class="font-semibold text-highlighted">{{
                  updateAge(activeVehicleDetail.reportTs)
                }}</span>
              </div>
              <div class="flex items-center justify-between gap-3">
                <span class="text-muted">Schedule</span>
                <UBadge :color="badge(activeVehicleDetail).color" variant="subtle">{{
                  badge(activeVehicleDetail).label
                }}</UBadge>
              </div>
            </section>
          </template>

          <div v-else class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
            <UBadge :color="badge(activeVehicle).color" variant="subtle">{{
              badge(activeVehicle).label
            }}</UBadge>
            <span class="text-muted">
              {{ activeSpeed ? `${Math.round(activeSpeed.mps * 3.6)} km/h` : 'Speed unavailable' }}
              <template v-if="activeSpeed?.estimated"> (estimated)</template>
            </span>
            <span v-if="activeVehicle.stale" class="text-warning">stale</span>
          </div>

          <UButton
            color="primary"
            trailing-icon="i-lucide-arrow-right"
            block
            :label="selectionActionLabel"
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
        :show-routes="visibleLayers.networkRoutes"
        :show-stops="visibleLayers.networkStops"
        before="vehicles-icons"
        dim
      />

      <MglGeoJsonSource
        v-if="selectedRoute?.line.length"
        source-id="selected-vehicle-route"
        :data="selectedRouteLine"
      >
        <MglLineLayer
          layer-id="selected-vehicle-route-casing"
          before="vehicles-icons"
          :paint="{ 'line-color': '#0f172a', 'line-width': 8, 'line-opacity': 0.4 }"
          :layout="{
            'line-cap': 'round',
            'line-join': 'round',
            visibility: visibleLayers.selectedTripLine ? 'visible' : 'none',
          }"
        />
        <MglLineLayer
          layer-id="selected-vehicle-route-line"
          before="vehicles-icons"
          :paint="{
            'line-color': '#dc2626',
            'line-width': 4,
            'line-opacity': 0.95,
          }"
          :layout="{
            'line-cap': 'round',
            'line-join': 'round',
            visibility: visibleLayers.selectedTripLine ? 'visible' : 'none',
          }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource
        v-if="selectedRoute?.stops.features.length"
        source-id="selected-vehicle-stops"
        :data="selectedRoute.stops"
      >
        <MglCircleLayer
          layer-id="selected-vehicle-stops-circles"
          before="vehicles-icons"
          :layout="{ visibility: visibleLayers.selectedTripStops ? 'visible' : 'none' }"
          :paint="{
            'circle-radius': ['match', ['get', 'state'], 'current', 9, 6],
            'circle-color': [
              'match',
              ['get', 'state'],
              'passed',
              '#94a3b8',
              'current',
              selectedRoute?.routeColor ?? '#2563eb',
              '#475569',
            ],
            'circle-stroke-width': ['match', ['get', 'state'], 'current', 3, 2],
            'circle-stroke-color': '#ffffff',
          }"
        />
      </MglGeoJsonSource>

      <MglGeoJsonSource source-id="vehicles" :data="vehiclesGeoJson">
        <MglSymbolLayer
          layer-id="vehicles-icons"
          :layout="{
            'icon-image': [
              'match',
              ['get', 'state'],
              'stale',
              ['case', ['get', 'hasBearing'], iconId('stale', true), iconId('stale', false)],
              'stopped',
              ['case', ['get', 'hasBearing'], iconId('stopped', true), iconId('stopped', false)],
              'matched',
              ['case', ['get', 'hasBearing'], iconId('matched', true), iconId('matched', false)],
              [
                'case',
                ['get', 'hasBearing'],
                iconId('unmatched', true),
                iconId('unmatched', false),
              ],
            ],
            'icon-rotate': ['get', 'bearing'],
            'icon-rotation-alignment': 'map',
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
            visibility: visibleLayers.vehicle ? 'visible' : 'none',
          }"
          @click="onVehicleClick"
        />
        <!-- This must be declared after its `before` target. vue-maplibre-gl
             adds child layers in declaration order once the source is ready. -->
        <MglCircleLayer
          layer-id="selected-vehicle-halo"
          before="vehicles-icons"
          :filter="['==', ['get', 'selected'], true]"
          :layout="{ visibility: visibleLayers.vehicle ? 'visible' : 'none' }"
          :paint="{
            'circle-radius': 18,
            'circle-color': selectedRoute?.routeColor ?? '#2563eb',
            'circle-opacity': 0.2,
            'circle-stroke-width': 3,
            'circle-stroke-color': selectedRoute?.routeColor ?? '#2563eb',
            'circle-stroke-opacity': 0.95,
          }"
        />
      </MglGeoJsonSource>
    </MglMap>

    <VehicleMapLegend class="absolute bottom-10 right-2" />

    <div class="absolute right-14 top-3 flex items-center gap-2">
      <UPopover>
        <UButton color="neutral" variant="subtle" icon="i-lucide-layers-3" />
        <template #content>
          <div class="w-52 space-y-2.5 p-3">
            <p class="text-xs font-semibold tracking-wide text-muted uppercase">Map layers</p>
            <UCheckbox v-model="visibleLayers.networkRoutes" label="Network routes" />
            <UCheckbox v-model="visibleLayers.networkStops" label="Network stops" />
            <UCheckbox v-model="visibleLayers.selectedTripLine" label="Selected trip line" />
            <UCheckbox v-model="visibleLayers.selectedTripStops" label="Selected trip stops" />
            <UCheckbox v-model="visibleLayers.vehicle" label="Vehicle marker" />
          </div>
        </template>
      </UPopover>
      <UButton color="neutral" variant="subtle" icon="i-lucide-scan" @click="fit" />
    </div>
  </div>
</template>
