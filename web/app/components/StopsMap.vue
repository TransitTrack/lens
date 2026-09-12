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
        <MglCircleLayer
          v-if="props.focusId"
          layer-id="focused-stop-halo"
          :filter="['==', ['get', 'stopId'], props.focusId]"
          :paint="{
            'circle-radius': 10,
            'circle-color': '#ffffff',
            'circle-opacity': 0.9,
            'circle-stroke-width': 2,
            'circle-stroke-color': '#4f46e5',
          }"
        />
        <MglCircleLayer
          layer-id="all-stops-circles"
          :paint="{
            'circle-radius': 4,
            'circle-color': '#475569',
            'circle-opacity': 0.9,
            'circle-stroke-width': 1.5,
            'circle-stroke-color': '#ffffff',
          }"
          @click="onClick"
        />
        <MglCircleLayer
          v-if="props.focusId"
          layer-id="focused-stop-core"
          :filter="['==', ['get', 'stopId'], props.focusId]"
          :paint="{
            'circle-radius': 5.5,
            'circle-color': '#4f46e5',
            'circle-stroke-width': 2,
            'circle-stroke-color': '#ffffff',
          }"
          @click="onClick"
        />
      </MglGeoJsonSource>
    </MglMap>
  </div>
</template>
