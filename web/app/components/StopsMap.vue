<script setup lang="ts">
import {useMapStyle} from '~/composables/useMapStyle'
import {boundsOf} from '~/utils/vehicleDetail'
import {extentToBounds} from '~/utils/mapBounds'
import type {LngLatBoundsExtent} from '~/composables/useFeedExtent'

const props = defineProps<{
  stops: GeoJSON.FeatureCollection
  /** stopId to highlight + centre on */
  focusId?: string | null
  /** agency service area — frames the map on load before the stops arrive */
  extent?: LngLatBoundsExtent | null
}>()
const emit = defineEmits<{ (e: 'select', stopId: string): void }>()

const MAP_ID = 'stops-map'
const mapStyle = useMapStyle()
const map = useMglMap(MAP_ID)

// Capture the agency extent once. `useFeedExtent` hands back a fresh object on
// every Apollo tick; binding it live would make <MglMap>'s bounds watcher snap
// the view back on every data refresh. A real feed switch remounts this page.
const initialBounds = shallowRef(extentToBounds(props.extent))
watch(
  () => props.extent,
  (e) => {
    if (!initialBounds.value) initialBounds.value = extentToBounds(e)
  },
)

const allCoords = computed<LngLat[]>(() =>
  props.stops.features
    .map((f) => f.geometry)
    .filter((g): g is GeoJSON.Point => g.type === 'Point')
    .map((g) => [g.coordinates[0]!, g.coordinates[1]!]),
)

// A stable signature of *which* stops are shown — changes when the filter/route
// changes, not when a poll rebuilds the same FeatureCollection.
const stopsKey = computed(() =>
  props.stops.features
    .map((f) => f.properties?.stopId)
    .join(','),
)

// The first fit is instant — the map already opened on the agency extent via
// `:bounds`. Later fits (the shown stop set actually changed) ease.
let firstFit = true

function fitAll() {
  const b = boundsOf(allCoords.value)
  if (!b || !map.map || !map.isLoaded) return
  map.map.resize()
  map.map.fitBounds(b, {padding: 40, maxZoom: 15, duration: firstFit ? 0 : 600})
  firstFit = false
}

watch([stopsKey, () => map.isLoaded], () => {
  fitAll()
  requestAnimationFrame(fitAll)
}, {immediate: true})

watch(
  () => props.focusId,
  (id) => {
    if (!id || !map.map || !map.isLoaded) return
    const f = props.stops.features.find((x) => x.properties?.stopId === id)
    if (f && f.geometry.type === 'Point') {
      map.map.easeTo({center: f.geometry.coordinates as [number, number], zoom: 15, duration: 600})
    }
  },
)

function onClick(e: { features?: { properties?: Record<string, unknown> }[] }) {
  const id = e.features?.[0]?.properties?.stopId
  if (typeof id === 'string') emit('select', id)
}

// Same halo treatment as VehicleHeadingMarker.vue: a white disc (with a
// soft shadow) behind the glyph. The glyph itself is a small map-pin
// symbol — like `.vhm__arrow` sits inside the vehicle's halo — sized to sit
// fully within the halo rather than poking a tail out past it.
function drawStopIcon(color: string): ImageData {
  const size = 45
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!

  const cx = size / 2
  const cy = size / 2
  const haloR = size * 0.45

  // .vhm--halo: background: white; box-shadow: 0 1px 4px rgb(0 0 0 / 0.35)
  ctx.beginPath()
  ctx.arc(cx, cy, haloR, 0, Math.PI * 2)
  ctx.fillStyle = '#888888'
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = 4
  ctx.shadowOffsetY = 1
  ctx.fill()

  // Pin symbol: head + tapered tail, capped with a white stroke like
  // `.vhm__arrow`'s `stroke="white"`, plus a punched-out window in the head.
  const headR = size * 0.175
  const headCy = cy - size * 0.1
  const tipY = cy + haloR * 0.72

  ctx.beginPath()
  ctx.arc(cx, headCy, headR, 0, Math.PI * 2)
  ctx.moveTo(cx - headR * 0.62, headCy + headR * 0.62)
  ctx.lineTo(cx, tipY)
  ctx.lineTo(cx + headR * 0.62, headCy + headR * 0.62)
  ctx.closePath()
  ctx.fillStyle = color
  ctx.fill('nonzero')
  ctx.lineWidth = 0.2
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(cx, headCy, headR * 0.42, 0, Math.PI * 2)
  ctx.fillStyle = '#ffffff'
  ctx.fill()

  return ctx.getImageData(0, 0, size, size)
}

const STOP_PIN = 'stop-pin'
const STOP_PIN_FOCUSED = 'stop-pin-focused'
const pinIcons: Record<string, ImageData> = {
  [STOP_PIN]: drawStopIcon('#FFF'),
  [STOP_PIN_FOCUSED]: drawStopIcon('#ef4444'),
}

function registerPinIcons() {
  const gl = map.map
  if (!gl) return
  for (const [id, image] of Object.entries(pinIcons)) {
    if (!gl.hasImage(id)) gl.addImage(id, image, {pixelRatio: 2})
  }
}

watch(
  () => map.isLoaded,
  (loaded) => {
    if (!loaded) return
    registerPinIcons()
    // Re-add after a style swap (e.g. light/dark basemap change) drops the sprite.
    map.map?.on('styleimagemissing', registerPinIcons)
  },
  {immediate: true},
)
</script>

<template>
  <div class="h-full w-full">
    <MglMap
      :map-key="MAP_ID"
      :map-style="mapStyle"
      :bounds="initialBounds"
      :fit-bounds-options="{ padding: 40, animate: false }"
      :center="[0, 0]"
      :zoom="2"
    >
      <MglNavigationControl/>
      <MglGeoJsonSource source-id="all-stops" :data="props.stops">
        <MglSymbolLayer
          layer-id="all-stops-pins"
          :layout="{
            'icon-image': ['case', ['==', ['get', 'stopId'], props.focusId ?? ''], STOP_PIN_FOCUSED, STOP_PIN],
            'icon-size': ['case', ['==', ['get', 'stopId'], props.focusId ?? ''], 1.2, 1],
            'icon-anchor': 'center',
            'icon-allow-overlap': true,
            'icon-ignore-placement': true,
          }"
          @click="onClick"
        />
      </MglGeoJsonSource>
    </MglMap>
  </div>
</template>
